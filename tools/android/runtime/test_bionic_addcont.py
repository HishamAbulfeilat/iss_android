"""Exercise installed add-on discovery and the AppContent mount/info APIs."""
import base64
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
CC = shutil.which(os.environ.get("CC", "cc"))


@unittest.skipUnless(CC, "C compiler unavailable")
class AddcontRuntimeTests(unittest.TestCase):
    def test_discovers_and_mounts_valid_content(self):
        with tempfile.TemporaryDirectory() as temp:
            binary = Path(temp) / "addcont-test"
            source = ROOT / "tools/android/runtime/c/test_addcont_runtime.c"
            subprocess.run([CC, "-std=c11", "-Wall", "-Wextra", "-Werror",
                            str(source), "-o", str(binary)], check=True,
                           capture_output=True, text=True)
            game = Path(temp) / "game"
            addon = game / "addcont/CUSA00223/UP9000-CUSA00223_00-ISS_DLC"
            addon.mkdir(parents=True)
            (addon / ".iss-content-id").write_text("UP9000-CUSA00223_00-ISS_DLC\n")
            invalid = game / "addcont/CUSA00223/UP9000-CUSA00223_00-IGNORED"
            invalid.mkdir()
            (invalid / ".iss-content-id").write_text("UP9000-CUSA00223_00-ISS_DLC\n")
            symlink_id = "UP9000-CUSA00223_00-SECOND"
            symlink_addon = game / "addcont/CUSA00223" / symlink_id
            symlink_addon.mkdir()
            marker = Path(temp) / "external-marker"
            marker.write_text(symlink_id + "\n")
            (symlink_addon / ".iss-content-id").symlink_to(marker)
            subprocess.run([str(binary), str(game)], check=True, capture_output=True, text=True)

    def test_appcontent_import_ids_match_the_nid_algorithm(self):
        salt = bytes.fromhex("518d64a635ded8c1e6b039b1c3e55230")
        table = (ROOT / "port/src/import_names.inc").read_text()
        names = ("sceAppContentGetAddcontInfoList", "sceAppContentGetAddcontInfo",
                 "sceAppContentAddcontMount", "sceAppContentAddcontUnmount")
        for name in names:
            digest = hashlib.sha1(name.encode("ascii") + salt).digest()[:8][::-1]
            nid = base64.b64encode(digest).decode().rstrip("=").replace("/", "-")
            self.assertIn(f'"{nid}#libSceAppContent","{name}"', table)


if __name__ == "__main__":
    unittest.main()
