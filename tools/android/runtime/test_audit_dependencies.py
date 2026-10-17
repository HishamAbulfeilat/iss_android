"""Prove recursive DT_NEEDED lookup, including fexdroid's PulseAudio library."""
from pathlib import Path
import tempfile
import unittest
from unittest import mock

import audit_dependencies as audit


class DependencyClosureTests(unittest.TestCase):
    def run_case(self, include_common=True):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            fs = root / "rootfs"
            bundle = root / "bundle"
            gen8 = root / "gen8"
            sysroot = root / "sysroot"
            python = root / "python"
            dest = root / "overlay"
            names = (
                (bundle, "bin/iss-probe"), (bundle, "bin/kgsl-chip-id"),
                (fs, "usr/bin/Xvfb"), (fs, "usr/libexec/fxshmd"),
                (fs, "usr/bin/python3.13"),
                (fs, "usr/lib/aarch64-linux-gnu/libvulkan_freedreno.so"),
                (fs, "usr/lib/aarch64-linux-gnu/libpulse.so.0"),
                (gen8, "libvulkan_freedreno_gen8.so"),
            )
            for tree, rel in names:
                target = tree / rel
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(b"mock ELF for controlled DT_NEEDED test")
            common = sysroot / "usr/lib/aarch64-linux-gnu/pulseaudio/libpulsecommon-17.0.so"
            if include_common:
                common.parent.mkdir(parents=True, exist_ok=True)
                common.write_bytes(b"arm64 simulated payload")
            # Mirror the production rootfs: Python comes from Debian overlay,
            # while fexdroid supplies the legacy display + audio runtime.
            (fs / "usr/bin/python3.13").unlink()
            python_bin = python / "usr/bin/python3.13"
            python_bin.parent.mkdir(parents=True)
            python_bin.write_bytes(b"overlay Python ARM64 ELF")
            dest.mkdir()
            def dependencies(path):
                if path.name == "iss-probe":
                    return ["libpulse.so.0"]
                if path.name == "libpulse.so.0":
                    return ["libpulsecommon-17.0.so"]
                return []
            with mock.patch.object(audit, "checked", side_effect=lambda path: path), \
                 mock.patch.object(audit, "deps", side_effect=dependencies):
                if include_common:
                    repaired = audit.complete(fs, bundle, gen8, sysroot, python, dest)
                    target = dest / "usr/lib/aarch64-linux-gnu/libpulsecommon-17.0.so"
                    self.assertTrue(target.is_file())
                    self.assertEqual(target.read_bytes(), b"arm64 simulated payload")
                    self.assertIn("libpulsecommon-17.0.so", repaired)
                else:
                    with self.assertRaisesRegex(RuntimeError, "UNRESOLVED ARM64 DT_NEEDED.*libpulsecommon"):
                        audit.complete(fs, bundle, gen8, sysroot, python, dest)

    def test_python_dependency_prefetch_is_used(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            rootfs, bundle, gen8, sysroot = [root / name for name in ("rootfs", "bundle", "gen8", "sysroot")]
            python, dest, stage = [root / name for name in ("python", "destination", "debian-stage")]
            for base, path in ((bundle, "bin/iss-probe"), (bundle, "bin/kgsl-chip-id"),
                               (rootfs, "usr/bin/Xvfb"), (rootfs, "usr/libexec/fxshmd"),
                               (rootfs, "usr/lib/aarch64-linux-gnu/libvulkan_freedreno.so"),
                               (gen8, "libvulkan_freedreno_gen8.so"),
                               (python, "usr/bin/python3.13"),
                               (python, "usr/lib/python3.13/lib-dynload/readline.so"),
                               (stage, "usr/lib/aarch64-linux-gnu/libreadline.so.8")):
                file = base / path
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_bytes(b"test-ELF")
            def dependencies(file):
                return ["libreadline.so.8"] if file.name == "readline.so" else []
            with mock.patch.object(audit, "checked", side_effect=lambda f: f), \
                 mock.patch.object(audit, "deps", side_effect=dependencies):
                repaired = audit.complete(rootfs, bundle, gen8, sysroot, python, dest, stage)
            self.assertIn("libreadline.so.8", repaired)
            self.assertTrue((dest / "usr/lib/aarch64-linux-gnu/libreadline.so.8").is_file())

    def test_transitive_pulsecommon_is_packaged(self):
        self.run_case()

    def test_build_is_blocked_on_a_missing_transitive_dependency(self):
        self.run_case(False)


if __name__ == "__main__":
    unittest.main()
