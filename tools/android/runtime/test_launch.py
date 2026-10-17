"""Regression tests for inFAMOUS game layout and Android startup defaults."""
import importlib.util
import os
from pathlib import Path
import tempfile
import unittest

LAUNCH = Path(__file__).with_name("launch.py")
spec = importlib.util.spec_from_file_location("android_iss_launch", LAUNCH)
launch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(launch)


class LayoutTests(unittest.TestCase):
    def make_game(self, root, resource="art"):
        game = Path(root) / "CUSA00223"
        (game / "sce_sys").mkdir(parents=True)
        (game / "sce_module").mkdir()
        (game / resource).mkdir()
        for path in ("eboot.bin", "sce_sys/param.sfo",
                     "sce_module/libc.prx", "sce_module/libSceFios2.prx"):
            (game / path).write_bytes(b"x")
        (game / resource / "cache").mkdir()
        return game

    def test_infamous_art_layout_accepted_without_psarc_or_dvdroot(self):
        with tempfile.TemporaryDirectory() as tmp:
            launch.check_game_layout(self.make_game(tmp))

    def test_gow3_layout_not_mistaken_for_infamous(self):
        with tempfile.TemporaryDirectory() as tmp:
            game = self.make_game(tmp, "dvdroot_ps4")
            with self.assertRaisesRegex(RuntimeError, "art"):
                launch.check_game_layout(game)

    def test_art_missing_reports_correct_folder(self):
        with tempfile.TemporaryDirectory() as tmp:
            game = self.make_game(tmp)
            (game / "art" / "cache").rmdir()
            (game / "art").rmdir()
            with self.assertRaisesRegex(RuntimeError, "art"):
                launch.check_game_layout(game)

    def test_empty_art_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            game = self.make_game(tmp)
            (game / "art" / "cache").rmdir()
            with self.assertRaisesRegex(RuntimeError, "empty art"):
                launch.check_game_layout(game)

    def test_empty_required_module_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            game = self.make_game(tmp)
            (game / "sce_module/libc.prx").write_bytes(b"")
            with self.assertRaisesRegex(RuntimeError, "libc.prx"):
                launch.check_game_layout(game)


class EnvironmentTests(unittest.TestCase):
    def test_iss_pc_defaults_preserved(self):
        with tempfile.TemporaryDirectory() as tmp:
            env = {"ISS_READBACKS": "0"}
            launch.setup_environment(env, Path(tmp))
            self.assertEqual(env["ISS_READBACKS"], "0")
            self.assertEqual(env["ISS_DEFERRED_READBACK"], "1")
            self.assertEqual(env["ISS_EAGER_READBACK"], "0")
            self.assertEqual(env["ISS_UPSCALER"], "off")
            self.assertEqual(env["ISS_CONFIG"], str(Path(tmp)/"tlg.ini"))
            self.assertNotIn("TLG_ENGINE_FPS", env)
            self.assertNotIn("TLG_READBACK_LINEAR", env)

if __name__ == "__main__":
    unittest.main()
