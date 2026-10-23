"""Language negotiation executes the production policy, including missing resources."""
from pathlib import Path
import importlib.util
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'port/scripts'))
from iss_language import configure_language,installed_packages,resolve_language,LANGUAGES

class GameLanguageTests(unittest.TestCase):
    def test_all_installed_languages_and_incomplete_fallback(self):
        for code,(number,token) in LANGUAGES.items():
            packages={'text':{token},'audio':{token}}
            self.assertEqual(resolve_language(code,packages)[:2],(number,code))
            for missing in ('text','audio'):
                incomplete={k:v.copy() for k,v in packages.items()};incomplete[missing].clear()
                self.assertEqual(resolve_language(code,incomplete)[0],1)
        self.assertEqual(resolve_language('xx',{'text':set(),'audio':set()})[0],1)

    def test_real_package_names_nonempty_files_patch_variants_and_no_symlinks(self):
        with tempfile.TemporaryDirectory() as tmp:
            game=Path(tmp);cache=game/'art/cache';cache.mkdir(parents=True)
            for name in ['lang_text_brazilian.xpp','lang_audio_brazilian_p.xpp']:
                (cache/name).write_bytes(b'archive')
            (cache/'lang_audio_german.xpp').write_bytes(b'')
            (cache/'lang_text_german.xpp.bak').write_bytes(b'archive')
            (cache/'lang_text_german.xpp').symlink_to(cache/'lang_text_brazilian.xpp')
            packages=installed_packages(game)
            self.assertEqual(packages,{'text':{'brazilian'},'audio':{'brazilian'}})
            env={'ISS_APP_LANGUAGE':'pt_BR','TLG_LANGUAGE':'8'}
            configure_language(env,game)
            self.assertEqual(env['TLG_LANGUAGE'],'17');self.assertEqual(env['ISS_LANGUAGE'],'17')
            env['ISS_APP_LANGUAGE']='de';configure_language(env,game)
            self.assertEqual(env['TLG_LANGUAGE'],'1')

    @unittest.skipUnless(shutil.which('java'),'Java unavailable')
    def test_android_locale_policy_and_actual_catalog(self):
        src=ROOT/'android/app/src/ro/cobrabm/fexdroid'
        language=(src/'AppLanguage.java').read_text()
        # Execute production resolution and catalog, independent of Android UI stubs.
        policy=language[language.index(' static String resolve('):language.index(' static Context attach(')]
        codes=language[language.index(' static final String[] CODES='):language.index(' static final String[] LABELS=')]
        test='''package ro.cobrabm.fexdroid;import java.util.*;
class LanguageTest {
 public static void main(String[] args){
  assert AppLanguage.resolve("auto",new Locale("pt","BR")).equals("pt");
  assert AppLanguage.resolve("auto",new Locale("ja","JP")).equals("ja");
  assert AppLanguage.resolve("auto",new Locale("hi","IN")).equals("en");
  assert AppLanguage.resolve("de",new Locale("pt","BR")).equals("de");
  assert AppLanguage.resolve("bad",Locale.FRENCH).equals("en");
  for(String code:new String[]{"es","fr","de","it","ru","ja","ko","zh"}){
   assert !LanguageCatalog.text(code,"Cancel").equals("Cancel"):code;
   assert !LanguageCatalog.text(code,"Select game folder").equals("Select game folder"):code;
   assert !LanguageCatalog.text(code,"Jump").equals("Jump"):code;
   assert LanguageCatalog.text(code,"Unknown diagnostic").equals("Unknown diagnostic");
  }
  assert LanguageCatalog.text("en","Cancel").equals("Cancel");
 }
}'''
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)
            (p/'AppLanguage.java').write_text('package ro.cobrabm.fexdroid;import java.util.Locale;final class AppLanguage{'+codes+policy+'}')
            (p/'LanguageCatalog.java').write_text((src/'LanguageCatalog.java').read_text())
            (p/'LanguageTest.java').write_text(test)
            result=subprocess.run(['java','com.sun.tools.javac.Main','-encoding','UTF-8','-d',tmp,*map(str,p.glob('*.java'))],capture_output=True,text=True)
            self.assertEqual(result.returncode,0,result.stderr)
            subprocess.run(['java','-ea','-cp',tmp,'ro.cobrabm.fexdroid.LanguageTest'],check=True,capture_output=True,text=True)
