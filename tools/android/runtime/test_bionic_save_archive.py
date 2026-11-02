"""Exercise real Java ZIP transfers, metadata, malformed archives and save/game locking."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
ROOT = Path(__file__).resolve().parents[3]
JAVA = shutil.which("java")

@unittest.skipUnless(JAVA, "JDK runtime unavailable")
class SaveArchiveTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        src = ROOT / "android/app/src/ro/cobrabm/fexdroid"
        subprocess.run([JAVA, "com.sun.tools.javac.Main", "-d", cls.temp.name,
                        str(src / "BionicSaveArchive.java"), str(src / "BionicSession.java"),
                        str(ROOT / "tools/android/runtime/java/BionicSaveArchiveTest.java")],
                       check=True, capture_output=True, text=True)
    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()
    def run_case(self, case):
        subprocess.run([JAVA, "-ea", "-cp", self.temp.name, "BionicSaveArchiveTest", case],
                       check=True, capture_output=True, text=True)
    def test_27_mib_roundtrip_preserves_metadata_and_other_slots(self):
        self.run_case("roundtrip")
    def test_bachata_metadata_and_raw_slot_layouts(self):
        self.run_case("bachata")
    def test_rejected_archives_leave_original_save_unchanged(self):
        self.run_case("invalid")
    def test_crc_error_leaves_original_save_unchanged(self):
        self.run_case("crc")
    def test_title_id_is_read_and_bounds_checked(self):
        self.run_case("title")
    def test_interrupted_import_is_recovered_before_launch(self):
        self.run_case("recovery")
    def test_game_and_save_transfers_cannot_overlap(self):
        self.run_case("lock")

if __name__ == "__main__":
    unittest.main()
