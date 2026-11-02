"""Verify Android pressure detection without requiring Vulkan or an Android SDK."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
CXX = shutil.which(os.environ.get("CXX", "c++"))


@unittest.skipUnless(CXX, "C++ compiler unavailable")
class AndroidMemoryTests(unittest.TestCase):
    def test_ram_and_swap_pressure(self):
        with tempfile.TemporaryDirectory() as temp:
            binary = Path(temp) / "android-memory"
            subprocess.run([CXX, "-std=c++17", "-Wall", "-Wextra", "-Werror",
                            str(ROOT / "tools/android/runtime/c/test_android_memory.cpp"),
                            "-o", str(binary)], check=True, capture_output=True, text=True)
            subprocess.run([str(binary)], check=True, capture_output=True, text=True)
