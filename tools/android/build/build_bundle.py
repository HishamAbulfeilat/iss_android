#!/usr/bin/env python3
"""Collect only ARM64 native dependencies not guaranteed in fexdroid's rootfs."""
from pathlib import Path
import argparse
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "out/arm64"
OURS = {
    "libisscpu.so": OUT / "fex/libisscpu.so",
    "libtlggpu.so": OUT / "gpu/libtlggpu.so",
}
FFMPEG = ("libavformat.so.61", "libavcodec.so.61", "libavutil.so.59",
          "libswscale.so.8", "libswresample.so.5")

def needed(path):
    result = subprocess.check_output(["llvm-readelf", "-d", str(path)], text=True)
    return [line.split("[", 1)[1].split("]", 1)[0]
            for line in result.splitlines() if "(NEEDED)" in line]

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--sysroot", type=Path, default=ROOT / ".local-deps/android/arm64-sysroot")
    p.add_argument("--rootfs", type=Path, default=ROOT / ".local-deps/android/migration/x/rootfs")
    a = p.parse_args()
    libdirs = [a.sysroot / "usr/lib/aarch64-linux-gnu", a.sysroot / "usr/lib",
               a.sysroot / "lib/aarch64-linux-gnu"]
    available = {path.name for path in (a.rootfs / "usr/lib/aarch64-linux-gnu").glob("*")}
    for directory in (a.rootfs / "usr/lib", a.rootfs / "lib"):
        available.update(path.name for path in directory.glob("*"))
    def locate(name):
        if name in OURS:
            return OURS[name]
        for directory in libdirs:
            if (directory / name).exists():
                return (directory / name).resolve()
        raise SystemExit(f"Missing ARM64 dependency: {name}")
    ship = {name: locate(name) for name in FFMPEG}
    ship.update(OURS)
    queue = [OUT / "iss-probe", *ship.values()]
    seen = set()
    while queue:
        for name in needed(queue.pop()):
            if name in seen:
                continue
            seen.add(name)
            if name in available and name not in ship and not name.startswith("libfmt.so."):
                continue
            if name not in ship:
                ship[name] = locate(name)
                queue.append(ship[name])
    dest = OUT / "bundle"
    if dest.exists():
        shutil.rmtree(dest)
    (dest / "bin").mkdir(parents=True)
    (dest / "lib").mkdir()
    shutil.copy2(OUT / "iss-probe", dest / "bin/iss-probe")
    for name, source in sorted(ship.items()):
        shutil.copy2(source.resolve(), dest / "lib" / name)
    for path in (dest / "lib").iterdir():
        subprocess.run(["patchelf", "--set-rpath", "$ORIGIN", str(path)], check=True)
        subprocess.run(["llvm-strip", "--strip-debug", str(path)], check=True)
    subprocess.run(["llvm-strip", "--strip-debug", str(dest / "bin/iss-probe")], check=True)
    print("ARM64 native bundle:", dest, len(ship), "libraries")

if __name__ == "__main__":
    main()
