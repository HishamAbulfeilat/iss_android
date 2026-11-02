"""Release assets must match the actual APK, original signer and embedded runtime."""
from pathlib import Path
import contextlib
import hashlib
import importlib.util
import io
import json
import tarfile
import tempfile
import unittest
from unittest import mock
import zipfile
ROOT=Path(__file__).resolve().parents[3]
SPEC=importlib.util.spec_from_file_location('iss_prepare_release',ROOT/'tools/android/bionic/prepare_release.py')
release=importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)
PIN='aff266dd2900f3aa51f41e1d767e9cc64afa974e115fa2aded997acced26475b'
COMMIT='a'*40

class ReleaseTests(unittest.TestCase):
    def run_case(self,failure=None):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            def put(path,value):
                p=root/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(value);return p
            put('android/app/BionicAndroidManifest.xml','<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.daedalus.iss.bionic" android:versionName="0.1" android:versionCode="7"/>')
            put('tools/android/bionic/signing-certificate.sha256',PIN+'\n')
            put('docs/RELEASE_NOTES_v0.1.md','Release notes\n');put('port/LICENSE','GPL\n')
            meta={'version':'0.1','source_commit':COMMIT,'application_id':'com.daedalus.iss.bionic','runtime_backend':'android-bionic'}
            if failure=='runtime_source':meta['source_commit']='b'*40
            runtime=io.BytesIO()
            with tarfile.open(fileobj=runtime,mode='w:gz') as tar:
                data=json.dumps(meta).encode();info=tarfile.TarInfo('runtime-manifest.json');info.size=len(data)
                tar.addfile(info,io.BytesIO(data))
            data=runtime.getvalue();apk=root/'inFAMOUS-Second-Son-Daedalus-v0.1.apk'
            with zipfile.ZipFile(apk,'w') as z:
                z.writestr('assets/runtime.tar.gz',data)
                z.writestr('assets/runtime.sha256','0'*64 if failure=='runtime_hash' else hashlib.sha256(data).hexdigest())
            def call(args,**kwargs):
                if args[0]=='aapt':
                    version='0.2.4-bionic' if failure=='apk_version' else '0.1'
                    return f"package: name='com.daedalus.iss.bionic' versionCode='7' versionName='{version}'\n"
                if args[0]=='apksigner':
                    return 'Signer #1 certificate SHA-256 digest: '+('0'*64 if failure=='signer' else PIN)+'\n'
                self.assertEqual(args,['git','rev-parse','HEAD']);return COMMIT+'\n'
            with mock.patch.object(release,'ROOT',root),mock.patch.object(release.subprocess,'check_output',side_effect=call),mock.patch('sys.argv',['prepare_release','--apk',str(apk),'--aapt','aapt','--apksigner','apksigner']),contextlib.redirect_stdout(io.StringIO()):
                if failure:
                    with self.assertRaises(SystemExit):release.main()
                    self.assertFalse((root/'RELEASE-METADATA.json').exists())
                    self.assertFalse(apk.with_suffix('.apk.sha256').exists())
                    return
                release.main()
            metadata=json.loads((root/'RELEASE-METADATA.json').read_text())
            self.assertEqual(metadata['source_commit'],COMMIT)
            self.assertEqual(metadata['version_code'],7)
            self.assertEqual(metadata['signer_certificate_sha256'],PIN)
            checksum=apk.with_suffix('.apk.sha256').read_text().strip().split()
            self.assertEqual(checksum,[hashlib.sha256(apk.read_bytes()).hexdigest(),apk.name])
            self.assertEqual((root/'RELEASE_NOTES.md').read_text(),'Release notes\n')
            self.assertEqual((root/'LICENSE.txt').read_text(),'GPL\n')

    def test_matching_release_creates_portable_assets(self):self.run_case()
    def test_different_certificate_is_rejected(self):self.run_case('signer')
    def test_actual_apk_version_mismatch_is_rejected(self):self.run_case('apk_version')
    def test_embedded_runtime_hash_mismatch_is_rejected(self):self.run_case('runtime_hash')
    def test_embedded_runtime_source_mismatch_is_rejected(self):self.run_case('runtime_source')

if __name__=='__main__':unittest.main()
