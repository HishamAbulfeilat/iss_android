#!/usr/bin/env python3
"""Validate the signed APK and prepare the public release assets without publishing."""
from pathlib import Path
import argparse
import hashlib
import io
import json
import re
import shutil
import subprocess
import tarfile
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[3]
ANDROID = '{http://schemas.android.com/apk/res/android}'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', required=True, type=Path)
    parser.add_argument('--aapt', required=True)
    parser.add_argument('--apksigner', required=True)
    args = parser.parse_args()
    xml = ET.parse(ROOT / 'android/app/BionicAndroidManifest.xml').getroot()
    version = xml.get(ANDROID + 'versionName')
    code = xml.get(ANDROID + 'versionCode')
    package = xml.get('package')
    badging = subprocess.check_output([args.aapt, 'dump', 'badging', str(args.apk)], text=True)
    for expected in (f"name='{package}'", f"versionCode='{code}'", f"versionName='{version}'"):
        if expected not in badging.splitlines()[0]:
            raise SystemExit('APK package/version does not match the release manifest')
    info = subprocess.check_output([args.apksigner, 'verify', '--verbose', '--print-certs',
                                    str(args.apk)], text=True)
    certs = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F:]+)$', info, re.M)
    certs = [v.replace(':', '').lower() for v in certs]
    pinned = (ROOT / 'tools/android/bionic/signing-certificate.sha256').read_text().strip()
    if certs != [pinned]:
        raise SystemExit('Release signer differs from Build 65; restore the original signing key')
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    with zipfile.ZipFile(args.apk) as apk:
        runtime = apk.read('assets/runtime.tar.gz')
        runtime_hash = hashlib.sha256(runtime).hexdigest()
        if runtime_hash != apk.read('assets/runtime.sha256').decode().strip():
            raise SystemExit('Packaged runtime checksum mismatch')
        with tarfile.open(fileobj=io.BytesIO(runtime), mode='r:gz') as tar:
            runtime_meta = json.load(tar.extractfile('runtime-manifest.json'))
            if (runtime_meta.get('version') != version or
                    runtime_meta.get('source_commit') != commit or
                    runtime_meta.get('application_id') != package or
                    runtime_meta.get('runtime_backend') != 'android-bionic'):
                raise SystemExit('Packaged runtime does not match the release source/version')
    digest = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    args.apk.with_suffix('.apk.sha256').write_text(f'{digest}  {args.apk.name}\n')
    metadata = {'version': version, 'version_code': int(code), 'application_id': package,
                'source_commit': commit, 'source_repository': 'https://github.com/erickmendes22/iss_android',
                'source_branch': 'experimental-bionic', 'apk': args.apk.name,
                'apk_sha256': digest, 'runtime_sha256': runtime_hash,
                'signer_certificate_sha256': pinned, 'min_android_api': 30,
                'abi': 'arm64-v8a', 'runtime_backend': 'android-bionic'}
    destination = args.apk.parent
    (destination / 'RELEASE-METADATA.json').write_text(json.dumps(metadata, indent=2) + '\n')
    shutil.copy2(ROOT / f'docs/RELEASE_NOTES_v{version}.md', destination / 'RELEASE_NOTES.md')
    shutil.copy2(ROOT / 'port/LICENSE', destination / 'LICENSE.txt')
    print(f'Release {version} verified: package, version, original signer, runtime and SHA-256')
    print(f'Source commit: {commit}')


if __name__ == '__main__':
    main()
