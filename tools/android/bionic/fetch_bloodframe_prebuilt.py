#!/usr/bin/env python3
"""Recover the pinned Bloodframe library from its verified v0.4.6 release payload."""
import argparse
import hashlib
import shutil
import tarfile
import tempfile
import urllib.request
import zipfile
from pathlib import Path

APK_URL = (
    "https://github.com/HishamAbulfeilat/iss_android/releases/download/"
    "v0.4.6-s25-20261031-310eed7/inFAMOUS-Second-Son-Daedalus-v0.4.6.apk"
)
APK_SHA256 = "20ac61335723e77e70d65703891800d89b3c3fe5b37f4a074ea5544c762a0d70"
RUNTIME_SHA256 = "bb7f7a5fad1e6252b24a4dbc55e3b16a40f6d4cc3e26c057b2526c19ada5508b"
BLOODFRAME_SHA256 = "141552bc5697cb2bea87f2f810c79605a55df60e62e18ca9edcb3585295dd30a"
RUNTIME_MEMBER = "assets/runtime.tar.gz"
LIBRARY_MEMBER = "issport/arm64/lib/libbloodframe.so"
LICENSE_PREFIX = "issport/licenses/"
LICENSE_FILES = (
    "bloodframe-dxbc.LICENSE",
    "bloodframe-lsfg-vk.LICENSE.md",
    "bloodframe-pe-parse.LICENSE",
    "bloodframe-source.txt",
    "bloodframe-volk.LICENSE.md",
)


def _sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _copy_verified_runtime(apk_path, output, apk_sha256=APK_SHA256,
                           runtime_sha256=RUNTIME_SHA256,
                           library_sha256=BLOODFRAME_SHA256):
    if _sha256_file(apk_path) != apk_sha256:
        raise ValueError("Historical APK checksum mismatch")

    output = Path(output)
    with tempfile.TemporaryDirectory(prefix="bloodframe-runtime-") as temp:
        runtime_path = Path(temp) / "runtime.tar.gz"
        with zipfile.ZipFile(apk_path) as apk:
            embedded_checksum = apk.read("assets/runtime.sha256").decode("ascii").split()[0]
            if embedded_checksum != runtime_sha256:
                raise ValueError("Embedded runtime checksum declaration mismatch")
            digest = hashlib.sha256()
            with apk.open(RUNTIME_MEMBER) as source, runtime_path.open("wb") as target:
                for chunk in iter(lambda: source.read(1024 * 1024), b""):
                    digest.update(chunk)
                    target.write(chunk)
        if digest.hexdigest() != runtime_sha256:
            raise ValueError("Embedded runtime checksum mismatch")

        with tarfile.open(runtime_path, "r:gz") as runtime:
            library = runtime.extractfile(LIBRARY_MEMBER)
            if library is None:
                raise ValueError("Verified runtime does not contain libbloodframe.so")
            library_data = library.read()
            if not library_data.startswith(b"\x7fELF"):
                raise ValueError("Pinned frame-generation library is not ELF")
            if hashlib.sha256(library_data).hexdigest() != library_sha256:
                raise ValueError("Pinned frame-generation library checksum mismatch")

            licenses = {}
            for name in LICENSE_FILES:
                member = runtime.extractfile(LICENSE_PREFIX + name)
                if member is None:
                    raise ValueError(f"Verified runtime is missing {name}")
                licenses[name] = member.read()

    lib_dir = output / "lib"
    license_dir = output / "licenses"
    lib_dir.mkdir(parents=True, exist_ok=True)
    license_dir.mkdir(parents=True, exist_ok=True)
    (lib_dir / "libbloodframe.so").write_bytes(library_data)
    for name, data in licenses.items():
        (license_dir / name).write_bytes(data)
    print(f"Restored verified libbloodframe.so to {lib_dir} (SHA-256 {library_sha256})")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path("out/bionic"),
                        help="Bionic output directory (default: out/bionic)")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="bloodframe-apk-") as temp:
        apk_path = Path(temp) / "historical.apk"
        request = urllib.request.Request(APK_URL, headers={"User-Agent": "iss-android-build"})
        with urllib.request.urlopen(request, timeout=120) as response, apk_path.open("wb") as target:
            shutil.copyfileobj(response, target, length=1024 * 1024)
        _copy_verified_runtime(apk_path, args.output)


if __name__ == "__main__":
    main()
