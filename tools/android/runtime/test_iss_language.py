"""Language negotiation executes the production policy, including missing resources."""
from pathlib import Path
import importlib.util
import os
import re
import struct
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'port/scripts'))
from iss_language import configure_language,installed_packages,resolve_language,LANGUAGES,playgo_languages

class GameLanguageTests(unittest.TestCase):
    def test_all_installed_languages_and_incomplete_fallback(self):
        for code,(number,token) in LANGUAGES.items():
            packages={'text':{token},'audio':{token}}
            self.assertEqual(resolve_language(code,packages)[:2],(number,code))
            for missing in ('text','audio'):
                incomplete={k:v.copy() for k,v in packages.items()};incomplete[missing].clear()
                self.assertEqual(resolve_language(code,incomplete)[0],1)
        self.assertEqual(resolve_language('xx',{'text':set(),'audio':set()})[0],1)

    def test_turkish_and_polish_game_languages_require_owned_assets(self):
        for code,number,token in (('tr',19,'turkish'),('pl',16,'polish')):
            self.assertEqual(LANGUAGES[code],(number,token))
            self.assertEqual(resolve_language(code,{'text':{token},'audio':{token}})[:2],(number,code))
            self.assertEqual(resolve_language(code,{'text':{token},'audio':set()})[0],1)
            self.assertEqual(resolve_language(code,{'text':set(),'audio':{token}})[0],1)
            self.assertEqual(resolve_language(code,{'text':set(),'audio':set()},{number})[0],number)

    def test_real_package_names_nonempty_files_patch_variants_and_no_symlinks(self):
        with tempfile.TemporaryDirectory() as tmp:
            game=Path(tmp);cache=game/'art/cache';cache.mkdir(parents=True)
            for name in ['lang_brazilian_text.xpp','lang_brazilian_audio.xpp']:
                (cache/name).write_bytes(b'archive')
            (cache/'lang_german_audio.xpp').write_bytes(b'')
            (cache/'lang_german_text_p.xpp').write_bytes(b'patch only')
            (cache/'lang_german_audio_p.xpp').write_bytes(b'patch only')
            (cache/'lang_german_text.xpp.bak').write_bytes(b'archive')
            (cache/'lang_german_text.xpp').symlink_to(cache/'lang_brazilian_text.xpp')
            packages=installed_packages(game)
            self.assertEqual(packages,{'text':{'brazilian'},'audio':{'brazilian'}})
            env={'ISS_APP_LANGUAGE':'pt_BR','TLG_LANGUAGE':'8'}
            configure_language(env,game)
            self.assertEqual(env['TLG_LANGUAGE'],'17');self.assertEqual(env['ISS_LANGUAGE'],'17')
            env['ISS_APP_LANGUAGE']='de';configure_language(env,game)
            self.assertEqual(env['TLG_LANGUAGE'],'1')

    def test_real_xpps_extensions_do_not_force_brazilian_to_english(self):
        with tempfile.TemporaryDirectory() as tmp:
            game=Path(tmp);cache=game/'art/cache';cache.mkdir(parents=True)
            for name in ('lang_brazilian_text.xpps','lang_brazilian_audio.xpps','lang_brazilian_text_p.xpps','lang_brazilian_audio_p.xpps'):
                (cache/name).write_bytes(b'archive')
            packages=installed_packages(game)
            self.assertEqual(resolve_language('pt_BR',packages)[:2],(17,'pt_BR'))
            env={'ISS_APP_LANGUAGE':'pt_BR'};configure_language(env,game)
            self.assertEqual(env['TLG_LANGUAGE'],'17')
            (cache/'lang_brazilian_audio.xpps').unlink()
            self.assertEqual(resolve_language('pt_BR',installed_packages(game))[0],1)

    def test_playgo_masks_and_malformed_tables(self):
        with tempfile.TemporaryDirectory() as tmp:
            game=Path(tmp);system=game/'sce_sys';system.mkdir()
            path=system/'playgo-chunk.dat'
            for endian in ('little','big'):
                data=bytearray(296);data[:4]=b'plgo';struct.pack_into('<H',data,10,3);struct.pack_into('<I',data,192,200)
                for index,mask in enumerate((0,(1<<64)-1,(1<<(63-17))|(1<<(63-1)))):
                    data[200+index*32+16:200+index*32+24]=mask.to_bytes(8,endian)
                path.write_bytes(data)
                self.assertEqual(playgo_languages(game),{1,17})
                self.assertEqual(resolve_language('pt_BR',{'text':set(),'audio':set()},playgo_languages(game))[0],17)
                # Metadata must not excuse a visibly incomplete language pair.
                self.assertEqual(resolve_language('pt_BR',{'text':{'brazilian'},'audio':set()},{17})[0],1)
            for bad in (b'',b'plgo',data[:210],b'bad!'+data[4:]):
                path.write_bytes(bad);self.assertEqual(playgo_languages(game),set())

    def test_turkish_polish_catalog_covers_all_android_ui_literals(self):
        src=ROOT/'android/app/src/ro/cobrabm/fexdroid'
        catalog=(src/'LanguageCatalogTrPl.java').read_text()
        keys=set(re.findall(r'\{\s*"([^"]+)"\s*,',catalog))
        self.assertGreaterEqual(len(keys),250)
        required=set()
        for source in src.glob('*.java'):
            if source.name.startswith('LanguageCatalog'):
                continue
            required.update(re.findall(r'AppLanguage\.text\(\s*"([^"]+)"\s*,',
                                       source.read_text()))
        self.assertFalse(required-keys,'Untranslated Android UI strings: '+str(sorted(required-keys)))

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
  assert AppLanguage.resolve("tr",new Locale("en","US")).equals("tr");
  assert AppLanguage.resolve("auto",new Locale("pl","PL")).equals("pl");
  assert AppLanguage.resolve("bad",Locale.FRENCH).equals("en");
  for(String code:new String[]{"es","fr","de","it","ru","ja","ko","zh","tr","pl"}){
   assert !LanguageCatalog.text(code,"Cancel").equals("Cancel"):code;
   assert !LanguageCatalog.text(code,"Select game folder").equals("Select game folder"):code;
   assert !LanguageCatalog.text(code,"Jump").equals("Jump"):code;
   if(code.equals("tr")||code.equals("pl")){
     assert !LanguageCatalog.text(code,"Disable shadows").equals("Disable shadows");
     assert !LanguageCatalog.text(code,"Preparing PKG installation…").equals("Preparing PKG installation…");
     assert !LanguageCatalog.text(code,"Cannot install driver").equals("Cannot install driver");
   }
   assert LanguageCatalog.text(code,"Unknown diagnostic").equals("Unknown diagnostic");
  }
  assert LanguageCatalog.text("en","Cancel").equals("Cancel");
 }
}'''
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)
            (p/'AppLanguage.java').write_text('package ro.cobrabm.fexdroid;import java.util.Locale;final class AppLanguage{'+codes+policy+'}')
            (p/'LanguageCatalog.java').write_text((src/'LanguageCatalog.java').read_text())
            (p/'LanguageCatalogTrPl.java').write_text((src/'LanguageCatalogTrPl.java').read_text())
            (p/'LanguageTest.java').write_text(test)
            result=subprocess.run(['java','com.sun.tools.javac.Main','-encoding','UTF-8','-d',tmp,*map(str,p.glob('*.java'))],capture_output=True,text=True)
            self.assertEqual(result.returncode,0,result.stderr)
            subprocess.run(['java','-ea','-cp',tmp,'ro.cobrabm.fexdroid.LanguageTest'],check=True,capture_output=True,text=True)
