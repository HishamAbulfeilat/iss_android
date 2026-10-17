#!/usr/bin/env python3
"""Game-free experimental runtime from verified fexdroid rootfs + ISS ARM64 binaries."""
from pathlib import Path
import hashlib
import io
import json
import lzma
import os
import posixpath
import stat
import sys
import tarfile

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "tools/android/inputs"))
from fetch_arm64_sysroot import MIRROR, SUITE, fetch, parse_packages, extract_deb
from fetch_rootfs import migrate_interpreters
sys.path.insert(0, str(ROOT / 'tools/android/runtime'))
from validate_runtime_elf import inspect

SOURCE = ROOT / ".local-deps/android/migration/x/rootfs"
BUNDLE = ROOT / "out/arm64/bundle"
OUT = ROOT / "out/release"
PYTHON_PACKAGES = ("python3.13-minimal", "libpython3.13-minimal", "libpython3.13-stdlib")
APP_LOADER = "/data/user/0/com.daedalus.iss/files/rootfs/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1"
EXCLUDES = ("dev", "proc", "sys", "run", "home", "root", "tmp", "var", "usr/include",
            "usr/share/man", "usr/share/doc", "etc/shadow", "etc/gshadow")

class PortableTar(tarfile.TarFile):
    def gettarinfo(self, name=None, arcname=None, fileobj=None):
        info = super().gettarinfo(name, arcname, fileobj)
        if info is not None and info.islnk():
            info.type = tarfile.REGTYPE
            info.linkname = ""
            info.size = os.stat(name).st_size
        return info

def keep(info):
    name = info.name.removeprefix("rootfs/").rstrip("/")
    if any(name == x or name.startswith(x + "/") for x in EXCLUDES):
        return None
    if info.isdev() or info.isfifo():
        return None
    if info.issym():
        if info.linkname.startswith("/"):
            target = info.linkname
            for prefix in ("/data/user/0/com.daedalus.iss/files/rootfs/",
                           "/data/data/com.daedalus.iss/files/rootfs/"):
                if target.startswith(prefix):
                    target = target[len(prefix):]
                    break
            else:
                if not target.startswith(("/usr/", "/lib/", "/bin/", "/sbin/", "/etc/")):
                    return None
                target = target.lstrip("/")
            if target.split("/", 1)[0] in ("lib", "bin", "sbin") and (SOURCE / target.split("/", 1)[0]).is_symlink():
                target = "usr/" + target.split("/", 1)[1]
            info.linkname = posixpath.relpath("rootfs/" + target, posixpath.dirname(info.name))
        resolved = posixpath.normpath(posixpath.join(posixpath.dirname(info.name), info.linkname))
        if not (resolved == "rootfs" or resolved.startswith("rootfs/")):
            return None
    info.uid = info.gid = 0
    info.uname = info.gname = ""
    return info

def python_overlay():
    directory = OUT / "python-overlay"
    if directory.exists() and (directory / "usr/bin/python3.13").is_file():
        return directory
    directory.mkdir(parents=True, exist_ok=True)
    index = parse_packages(lzma.decompress(fetch(f"{MIRROR}/dists/{SUITE}/main/binary-arm64/Packages.xz")).decode())
    for name in PYTHON_PACKAGES:
        fields = index[name]
        blob = fetch(f"{MIRROR}/{fields['Filename']}")
        if hashlib.sha256(blob).hexdigest() != fields["SHA256"]:
            raise RuntimeError("Debian package SHA256 mismatch: " + name)
        extract_deb(blob, directory)
    return directory

def validate_inputs():
    for path in (SOURCE / "usr/bin/Xvfb", SOURCE / "usr/libexec/fxshmd",
                 SOURCE / "usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1",
                 BUNDLE / "bin/iss-probe", BUNDLE / "lib/libisscpu.so",
                 BUNDLE / "lib/libtlggpu.so"):
        if not path.is_file():
            raise SystemExit("Missing game-free runtime input: " + str(path))
    for key in ("usr/bin/mkdir", "usr/bin/Xvfb", "usr/libexec/fxshmd",
                "usr/lib/aarch64-linux-gnu/libvulkan_freedreno.so"):
        if not (SOURCE / key).is_file():
            raise SystemExit("Incomplete fexdroid rootfs: " + key)
    if not (SOURCE / "usr/share/vulkan/icd.d/freedreno_icd.aarch64.json").is_file():
        raise SystemExit("Missing Vulkan ICD JSON in fexdroid rootfs")
    if not (ROOT / "tools/android/runtime/launch.py").is_file():
        raise SystemExit("Missing Python game launcher")
    if not (ROOT / "tools/android/device/run-iss.sh").is_file():
        raise SystemExit("Missing inFAMOUS game startup script")

def add_bytes(tar, path, content, mode=0o644):
    info = tarfile.TarInfo(path)
    info.size = len(content)
    info.mode = mode
    tar.addfile(info, io.BytesIO(content))

def main():
    validate_inputs()
    # Cache entries produced by earlier builds pointed PT_INTERP at com.iss.android.
    # Repair cached binaries as well as freshly extracted rootfs, then validate before signing.
    migrated = migrate_interpreters(SOURCE)
    print(f"Rootfs ELF interpreter migration: {migrated} corrected")
    for name in ("usr/bin/mkdir", "usr/bin/Xvfb", "usr/libexec/fxshmd"):
        record = inspect(SOURCE / name)
        if not record or record["machine"] != 183:
            raise SystemExit("Rootfs executable is not AArch64: " + name)
        interp = record["interpreter"] or ""
        if "com.iss.android" in interp or "ro.cobrabm.fexdroid" in interp:
            raise SystemExit("Stale interpreter in rootfs: " + name + " " + interp)
    OUT.mkdir(parents=True, exist_ok=True)
    overlay = python_overlay()
    archive = OUT / "runtime.tar.gz"
    with PortableTar.open(archive, "w:gz", compresslevel=6, format=tarfile.PAX_FORMAT, dereference=False) as tar:
        def safe_root_member(info):
            if info.name == "rootfs/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json":
                return None
            return keep(info)
        tar.add(SOURCE, arcname="rootfs", filter=safe_root_member)
        for child in sorted(overlay.iterdir()):
            tar.add(child, arcname="rootfs/" + child.name, filter=keep)
        for folder in ("rootfs/tmp", "rootfs/tmp/.X11-unix", "rootfs/tmp/.fxshm",
                       "rootfs/run", "rootfs/var", "rootfs/home", "home",
                       "home/iss", "issport/out", "issport/patches", "issport/user"):
            info = tarfile.TarInfo(folder)
            info.type = tarfile.DIRTYPE
            info.mode = 0o1777 if folder.startswith("rootfs/tmp") else 0o755
            tar.addfile(info)
        for child in ("bin", "lib"):
            tar.add(BUNDLE / child, arcname="issport/" + child)
        scripts = ROOT / "port/scripts"
        for script in sorted(scripts.glob("*.py")):
            tar.add(script, arcname="issport/scripts/" + script.name)
        tar.add(ROOT / "tools/android/device/run-iss.sh", arcname="issport/run-iss.sh",
                filter=lambda info: fix_mode(info))
        tar.add(ROOT / "tools/android/runtime/launch.py", arcname="issport/launch.py")
        # The JSON must point to the current APK's private rootfs, never another port.
        icd = SOURCE / "usr/share/vulkan/icd.d/freedreno_icd.aarch64.json"
        if icd.is_file():
            payload = json.loads(icd.read_text())
            if "ICD" not in payload or not isinstance(payload["ICD"], dict):
                raise SystemExit("Invalid fexdroid Vulkan ICD")
            payload["ICD"]["library_path"] = APP_LOADER.replace(
                "ld-linux-aarch64.so.1", "libvulkan_freedreno.so")
            add_bytes(tar, "rootfs/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json",
                      (json.dumps(payload, indent=2) + "\n").encode())
    # No game content, personal save data, or decrypted modules may be embedded.
    with tarfile.open(archive, "r:gz") as t:
        names = t.getnames()
    forbidden = ("eboot.bin", "boot-linked.bin", "sce_module/", "sce_sys/", "savedata/")
    if any(any(word in name for word in forbidden) for name in names):
        archive.unlink()
        raise SystemExit("Game content detected; refusing to package.")
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    (OUT / "runtime.sha256").write_text(digest + "\n")
    print(f"Runtime: {archive.stat().st_size:,} bytes sha256={digest}")

def fix_mode(info):
    info.mode = 0o755
    return info

if __name__ == "__main__":
    main()
