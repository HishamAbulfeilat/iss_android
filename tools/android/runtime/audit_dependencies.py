#!/usr/bin/env python3
"""Audit the recursive ARM64 DT_NEEDED closure, repairing missing Debian libraries.

A pre-existing fexdroid .so can have unresolved dependencies: inspecting only
the linker output of iss-probe, CPU and GPU libraries cannot detect these.
"""
from collections import deque
from pathlib import Path
import re
import shutil
import subprocess
from validate_runtime_elf import inspect

NEEDED = re.compile(r"\(NEEDED\).*?\[([^\]]+)\]")
# These directories are searched at runtime through LD_LIBRARY_PATH (or system
# defaults). The final pulseaudio directory is a SOURCE location, not a runtime
# default: any needed library from it is staged into the primary library folder.
SEARCH_DIRS = ("usr/lib/aarch64-linux-gnu", "usr/lib", "lib/aarch64-linux-gnu",
               "lib", "usr/local/lib")
SOURCE_DIRS = (*SEARCH_DIRS, "usr/lib/aarch64-linux-gnu/pulseaudio")
PROTECTED_ABI = {"libc.so.6", "libm.so.6", "libpthread.so.0", "libdl.so.2",
                 "librt.so.1", "libresolv.so.2", "ld-linux-aarch64.so.1"}


def deps(path):
    output = subprocess.check_output(["llvm-readelf", "-d", "--wide", str(path)],
                                     text=True, stderr=subprocess.STDOUT)
    return NEEDED.findall(output)


def find(root, name, dirs=SEARCH_DIRS):
    for dirname in dirs:
        candidate = root / dirname / name
        if candidate.is_file():
            return candidate
    return None


def checked(path):
    meta = inspect(path)
    if not meta or meta["machine"] != 183:
        raise RuntimeError(f"Expected Linux AArch64 ELF dependency, got {path}")
    return path


def complete(rootfs, bundle, gen8, sysroot, python_overlay, destination):
    rootfs, bundle, gen8 = map(Path, (rootfs, bundle, gen8))
    sysroot, python_overlay, destination = map(Path, (sysroot, python_overlay, destination))
    bundled = {p.name: p for p in (bundle / "lib").glob("*.so*") if p.is_file()}
    roots = [bundle / "bin/iss-probe", bundle / "bin/kgsl-chip-id",
             rootfs / "usr/bin/Xvfb", rootfs / "usr/libexec/fxshmd",
             rootfs / "usr/bin/python3.13",
             rootfs / "usr/lib/aarch64-linux-gnu/libvulkan_freedreno.so",
             gen8 / "libvulkan_freedreno_gen8.so"]
    vulkan = find(rootfs, "libvulkan.so.1")
    if vulkan:
        roots.append(vulkan)
    roots.extend(bundled.values())
    # CPython dynamically loads extension modules; inspect the packaged ones.
    py_lib = python_overlay / "usr/lib"
    if py_lib.is_dir():
        roots.extend(p for p in py_lib.rglob("*.so") if p.is_file() and not p.is_symlink())
    queue = deque(roots)
    visited = set()
    required = set()
    copied = {}
    for path in roots:
        if not path.is_file():
            raise RuntimeError(f"ARM64 runtime audit root missing: {path}")
    while queue:
        path = queue.popleft().resolve()
        if path in visited:
            continue
        visited.add(path)
        checked(path)
        for name in deps(path):
            if not name or "/" in name or name in (".", ".."):
                raise RuntimeError(f"unsafe DT_NEEDED {name!r} in {path}")
            required.add(name)
            found = bundled.get(name)
            if found is None:
                for base in (destination, rootfs, python_overlay):
                    found = find(base, name)
                    if found is not None:
                        break
            if found is None:
                replacement = find(rootfs, name, SOURCE_DIRS) or find(sysroot, name, SOURCE_DIRS)
                if replacement is None:
                    raise RuntimeError(f"UNRESOLVED ARM64 DT_NEEDED: {name} required by {path}")
                if name in PROTECTED_ABI:
                    raise RuntimeError(f"rootfs glibc dependency missing: {name} (needed by {path})")
                checked(replacement.resolve())
                found = destination / "usr/lib/aarch64-linux-gnu" / name
                found.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(replacement.resolve(), found)
                copied[name] = str(replacement)
            queue.append(found)
    available = set(bundled)
    for base in (rootfs, python_overlay, destination):
        for dirname in SEARCH_DIRS:
            folder = base / dirname
            if folder.is_dir():
                available.update(p.name for p in folder.iterdir() if p.is_file())
    missing = required - available
    if missing:
        raise RuntimeError("Unresolved packaged ARM64 dependencies: " + ", ".join(sorted(missing)))
    print(f"ARM64 dependency closure: {len(visited)} ELFs, {len(required)} sonames, "
          f"{len(copied)} packaged missing dependencies", flush=True)
    for name, original in sorted(copied.items()):
        print(f"  ARM64 dependency repaired: {name} <- {original}", flush=True)
    return copied
