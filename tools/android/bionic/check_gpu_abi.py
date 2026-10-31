#!/usr/bin/env python3
"""Validate actual Android loader->GPU C ABI before linking the native probe.

Use the ARM64 linker inputs, not a guessed list of interfaces: the GPU is
linked with '-z undefs' because host callbacks are provided by iss-probe,
but every tlggpu_* function called by the loader must be exported by GPU.
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path


def symbols(nm: Path, flags: list[str], files: list[Path]) -> set[str]:
    if not files:
        raise ValueError("no native objects found for ABI audit")
    command = [str(nm), *flags, *[str(x) for x in files]]
    output = subprocess.check_output(command, text=True, stderr=subprocess.STDOUT)
    # llvm-nm -j emits one symbol per line, with optional object labels.
    return set(re.findall(r"(?m)^\s*(tlggpu_[A-Za-z0-9_]+)\s*$", output))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--ndk", required=True, type=Path)
    ap.add_argument("--root", required=True, type=Path)
    args = ap.parse_args()
    nm = args.ndk / "toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm"
    gpu = args.root / "lib/libtlggpu.so"
    objects = sorted((args.root / "runtime-objects").glob("*.o"))
    if not nm.is_file() or not gpu.is_file() or not objects:
        print("Bionic GPU ABI audit incomplete: missing LLVM nm, GPU or preflight loader objects", file=sys.stderr)
        return 1
    required = symbols(nm, ["-u", "-j"], objects)
    exported = symbols(nm, ["-D", "--defined-only", "-j"], [gpu])
    if not required:
        print("Bionic GPU ABI audit incomplete: loader did not reference any tlggpu_* APIs", file=sys.stderr)
        return 1
    missing = sorted(required - exported)
    if missing:
        print("Bionic GPU ABI MISMATCH: missing exported Android APIs:", file=sys.stderr)
        for symbol in missing:
            print(f"  {symbol}", file=sys.stderr)
        return 1
    print(f"Bionic GPU ABI OK: {len(required)} loader/HLE tlggpu_* symbols resolved by libtlggpu.so")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
