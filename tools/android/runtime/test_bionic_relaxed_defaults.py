"""Exercise the real launch environment, including explicit diagnostic overrides."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
spec = importlib.util.spec_from_file_location("iss_bionic_relaxed", Path(__file__).with_name("launch.py"))
launch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(launch)

class BionicDefaultsTests(unittest.TestCase):
    def setup_env(self, **values):
        env = dict(values)
        with tempfile.TemporaryDirectory() as directory:
            return launch.setup_environment(env, Path(directory))
    def test_bionic_uses_relaxed_and_live_memory_budget(self):
        env = self.setup_env(ISS_BIONIC="1")
        self.assertEqual(env["ISS_READBACKS"], "1")
        self.assertNotIn("ISS_GC_BUDGET_MB", env)
        self.assertNotIn("TLG_GC_BUDGET_MB", env)
        self.assertNotIn("TLG_READBACK_LINEAR", env)
        self.assertEqual(env["ISS_DEFERRED_READBACK"], "1")
    def test_explicit_readback_and_budget_overrides_survive(self):
        for mode in ("0", "1", "2"):
            with self.subTest(mode=mode):
                env = self.setup_env(ISS_BIONIC="1", ISS_READBACKS=mode,
                                     TLG_READBACKS=mode, ISS_GC_BUDGET_MB="2800")
                self.assertEqual(env["ISS_READBACKS"], mode)
                self.assertEqual(env["TLG_READBACKS"], mode)
                self.assertEqual(env["ISS_GC_BUDGET_MB"], "2800")
    def test_non_bionic_launch_defaults_are_not_changed(self):
        env = self.setup_env()
        self.assertEqual(env["ISS_READBACKS"], "2")
        self.assertEqual(env["ISS_GC_BUDGET_MB"], "3500")

if __name__ == "__main__":
    unittest.main()
