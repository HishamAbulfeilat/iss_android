"""Prevent shipping a Bionic APK with an invalid /system/bin/sh launcher.

Regression for the 2026-10-17 phone error:
run-bionic.sh[52]: no closing quote
Caused by an unescaped apostrophe in a ${FXD_FILES:?...} expansion.
"""
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[3]
LAUNCHER = ROOT / "tools/android/bionic/run-bionic.sh"


class BionicShellSyntaxTests(unittest.TestCase):
    def test_launcher_parses_as_android_compatible_sh(self):
        # POSIX-like shells catch the same EOF quote error as Android mksh.
        for shell in ("sh", "bash"):
            with self.subTest(shell=shell):
                proc = subprocess.run(
                    [shell, "-n", str(LAUNCHER)],
                    capture_output=True, text=True,
                )
                self.assertEqual(0, proc.returncode, proc.stderr)

    def test_missing_files_message_does_not_reopen_single_quote(self):
        # A regression test in addition to syntax checks: the original
        # apostrophe in this *unquoted* parameter expansion crashed Android.
        content = LAUNCHER.read_text()
        self.assertIn("files=${FXD_FILES:?ISS Bionic requires app private data directory}", content)


if __name__ == "__main__":
    unittest.main()
