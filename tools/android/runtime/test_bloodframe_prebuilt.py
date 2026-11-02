"""Verify the checksum-gated Bloodframe fallback extraction."""
import hashlib
import io
from pathlib import Path
import sys
import tarfile
import tempfile
import unittest
import zipfile

BIONIC_DIR = Path(__file__).resolve().parents[1] / "bionic"
sys.path.insert(0, str(BIONIC_DIR))
import fetch_bloodframe_prebuilt as fallback


def make_apk(path):
    runtime_bytes = io.BytesIO()
    library = b"\x7fELF" + b"test Bloodframe ARM64 payload"
    with tarfile.open(fileobj=runtime_bytes, mode="w:gz") as runtime:
        files = {
            fallback.LIBRARY_MEMBER: library,
            **{fallback.LICENSE_PREFIX + name: name.encode("ascii")
               for name in fallback.LICENSE_FILES},
        }
        for name, content in files.items():
            info = tarfile.TarInfo(name)
            info.size = len(content)
            runtime.addfile(info, io.BytesIO(content))
    runtime_data = runtime_bytes.getvalue()
    with zipfile.ZipFile(path, "w") as apk:
        apk.writestr(fallback.RUNTIME_MEMBER, runtime_data)
        apk.writestr("assets/runtime.sha256", hashlib.sha256(runtime_data).hexdigest() + "\n")
    return hashlib.sha256(path.read_bytes()).hexdigest(), hashlib.sha256(runtime_data).hexdigest(), \
        hashlib.sha256(library).hexdigest()


class BloodframeFallbackTests(unittest.TestCase):
    def test_extracts_only_the_verified_library_and_licenses(self):
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / "test.apk"
            apk_hash, runtime_hash, library_hash = make_apk(apk)
            output = Path(temp) / "out"
            fallback._copy_verified_runtime(apk, output, apk_hash, runtime_hash, library_hash)
            self.assertEqual((output / "lib/libbloodframe.so").read_bytes(),
                             b"\x7fELF" + b"test Bloodframe ARM64 payload")
            for name in fallback.LICENSE_FILES:
                self.assertEqual((output / "licenses" / name).read_bytes(), name.encode("ascii"))

    def test_rejects_unpinned_apk(self):
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / "test.apk"
            _, runtime_hash, library_hash = make_apk(apk)
            with self.assertRaisesRegex(ValueError, "APK checksum"):
                fallback._copy_verified_runtime(apk, Path(temp) / "out", "0" * 64,
                                                runtime_hash, library_hash)

    def test_rejects_changed_library(self):
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / "test.apk"
            apk_hash, runtime_hash, _ = make_apk(apk)
            with self.assertRaisesRegex(ValueError, "library checksum"):
                fallback._copy_verified_runtime(apk, Path(temp) / "out", apk_hash,
                                                runtime_hash, "0" * 64)


if __name__ == "__main__":
    unittest.main()
