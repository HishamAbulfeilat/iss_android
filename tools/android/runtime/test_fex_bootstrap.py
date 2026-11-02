"""Prevent game-foreign x86 patches and unregistered FEX executable stubs.

The inFAMOUS port must never install God of War III draw-distance hooks on
CUSA00004; all dynamically generated code executed by FEX must be registered.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
PROBE = ROOT / "port/src/probe.c"
CPU = ROOT / "cpu/guest_cpu_fex.cpp"


def function_source(source, start, end):
    a = source.index(start)
    b = source.index(end, a+len(start))
    return source[a:b]


class InFamousFexBootstrapTests(unittest.TestCase):
    def test_gow3_draw_distance_is_serial_and_version_gated(self):
        source = PROBE.read_text()
        view = function_source(source, "static void install_view_tuning(", "/* G3PATCH2")
        self.assertIn('strcmp(serial, "CUSA01623") || strcmp(version, "01.02")', view)
        self.assertLess(view.index('if (strcmp(serial'), view.index('tlg_view_signature_match'))
        self.assertIn('install_view_tuning(app0, segments, ns, size)', source)

    def test_dynamic_code_stubs_are_registered_for_fex(self):
        source = PROBE.read_text()
        regions = [
            ("static void install_cheats(", "/* Damage hook"),
            ("static void install_actor_watch(", "static void install_orb_multiplier("),
            ("static void install_orb_multiplier(", "/* Writes a jump"),
            ("static void install_stat_multipliers(", "extern uintptr_t tlg_view_region_params"),
            ("static void install_view_tuning(", "/* G3PATCH2"),
        ]
        for start, end in regions:
            with self.subTest(start=start):
                func = function_source(source, start, end)
                self.assertIn("guest_cpu_code((uintptr_t)", func)
                self.assertLess(func.index("protect("), func.index("guest_cpu_code((uintptr_t)"))

    def test_unknown_guest_code_is_not_blindly_marked_executable(self):
        text = CPU.read_text()
        func = function_source(text, "QueryGuestExecutableRange(", "LookupExecutableFileSection(")
        self.assertIn("return {0, 0, false};", func)
        self.assertIn("code_ranges", func)


if __name__ == "__main__":
    unittest.main()
