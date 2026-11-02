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
from fetch_arm64_sysroot import MIRROR, SUITE, fetch, parse_packages, extract_deb, dependencies
from fetch_rootfs import migrate_interpreters
sys.path.insert(0, str(ROOT / 'tools/android/runtime'))
from validate_runtime_elf import inspect
from audit_dependencies import complete

SOURCE = ROOT / ".local-deps/android/migration/x/rootfs"
BUNDLE = ROOT / "out/arm64/bundle"
OUT = ROOT / "out/release"
GEN8 = ROOT / "out/arm64/turnip-gen8"
GEN8_LIBRARY = "libvulkan_freedreno_gen8.so"
GEN8_ICD = "freedreno_gen8_icd.aarch64.json"
PYTHON_PACKAGES = ("python3.13-minimal", "libpython3.13-minimal", "libpython3.13-stdlib")
# CPython's stdlib extension modules link to libraries NOT covered by our C/C++
# build sysroot.  Stage the runtime package dependencies from the SAME Debian
# trixie arm64 index that supplies Python, then ship only the needed SONAMEs.
# The extras cover extensions such as _ssl, _gdbm, _nis and _curses_panel.
PYTHON_EXTRA_LIB_PACKAGES = (
    "libssl3t64", "libgdbm6t64", "libgdbm-compat4t64",
    "libcrypt1", "libnsl2", "libtirpc3t64",
)
PROTECTED_RUNTIME_PACKAGES = {"libc6", "libc6-dev", "libstdc++6", "libgcc-s1",
                              "libpython3.13-minimal", "libpython3.13-stdlib"}

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

def debian_index():
    # Reuse the already validated Packages index downloaded by fetch_arm64_sysroot.
    cached = ROOT / ".local-deps/android/arm64-debs" / f"Packages-{SUITE}-binary-arm64.xz"
    data = cached.read_bytes() if cached.is_file() else fetch(
        f"{MIRROR}/dists/{SUITE}/main/binary-arm64/Packages.xz")
    return parse_packages(lzma.decompress(data).decode())


def debian_package(name, index, directory):
    fields = index.get(name)
    if fields is None:
        raise RuntimeError(f"Debian {SUITE} arm64 package not found: {name}")
    blob = fetch(f"{MIRROR}/{fields['Filename']}")
    if hashlib.sha256(blob).hexdigest() != fields["SHA256"]:
        raise RuntimeError("Debian package SHA256 mismatch: " + name)
    extract_deb(blob, directory)


def python_overlay(index):
    directory = OUT / "python-overlay"
    if directory.exists() and (directory / "usr/bin/python3.13").is_file():
        return directory
    directory.mkdir(parents=True, exist_ok=True)
    for name in PYTHON_PACKAGES:
        debian_package(name, index, directory)
    return directory


def selected_runtime_packages(index):
    """Resolve library *package* dependencies too, not just Python's first level.

    Some stdlib extensions need libnsl -> libtirpc -> libgssapi -> libkrb5,
    and each of those can reference yet more libraries at runtime.
    """
    pending = list(PYTHON_EXTRA_LIB_PACKAGES)
    for name in PYTHON_PACKAGES:
        if name not in index:
            raise RuntimeError(f"Python Debian package missing from index: {name}")
        pending.extend(dependencies(index[name]))
    selected = set()
    while pending:
        name = pending.pop()
        if name in selected or name in PROTECTED_RUNTIME_PACKAGES:
            continue
        if not name.startswith(("lib", "zlib")):
            continue  # files other than native runtime libraries are not needed here
        info = index.get(name)
        if info is None:
            raise RuntimeError(f"Debian {SUITE} arm64 runtime package not found: {name}")
        selected.add(name)
        pending.extend(dependencies(info))
    return sorted(selected)


def runtime_library_inputs(index):
    """Prepare verified Debian ARM64 library packages before expensive native builds."""
    import shutil
    directory = OUT / "debian-runtime-lib-input"
    requested = selected_runtime_packages(index)
    digest = hashlib.sha256("\n".join(
        f"{name} {index[name]['SHA256']}" for name in requested).encode()).hexdigest()
    marker = directory / ".package-set.sha256"
    if (marker.is_file() and marker.read_text().strip() == digest
            and any(directory.rglob("*.so*"))):
        print(f"Debian ARM64 runtime inputs reused: {len(requested)} packages", flush=True)
        return directory
    if directory.exists():
        shutil.rmtree(directory)
    directory.mkdir(parents=True)
    for name in requested:
        debian_package(name, index, directory)
    marker.write_text(digest + "\n")
    print(f"Debian ARM64 runtime inputs prepared: {len(requested)} packages", flush=True)
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
    for required in (GEN8 / GEN8_LIBRARY, GEN8 / GEN8_ICD,
                     BUNDLE / "bin/kgsl-chip-id"):
        if not required.is_file() or required.stat().st_size == 0:
            raise SystemExit("Missing compiled Linux/glibc Turnip Gen8 input: " + str(required))
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
    # Turnip Gen8 must be an AArch64 Linux shared object, never an Android/Bionic .so.
    driver_info = inspect(GEN8 / GEN8_LIBRARY)
    if not driver_info or driver_info["machine"] != 183 or not driver_info["dynamic"]:
        raise SystemExit("Turnip Gen8 output is not an ARM64 Linux dynamic library")
    OUT.mkdir(parents=True, exist_ok=True)
    index = debian_index()
    overlay = python_overlay(index)
    debian_runtime = runtime_library_inputs(index)
    # Complete the graph reachable from the native entrypoints and Vulkan ICDs.
    # Existing fexdroid libraries can themselves depend on missing .so files.
    extra = OUT / "dependency-overlay"
    if extra.exists():
        import shutil
        shutil.rmtree(extra)
    extra.mkdir(parents=True, exist_ok=True)
    copied = complete(SOURCE, BUNDLE, GEN8, ROOT / ".local-deps/android/arm64-sysroot", overlay, extra, debian_runtime)
    archive = OUT / "runtime.tar.gz"
    with PortableTar.open(archive, "w:gz", compresslevel=6, format=tarfile.PAX_FORMAT, dereference=False) as tar:
        def safe_root_member(info):
            if info.name == "rootfs/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json":
                return None
            return keep(info)
        tar.add(SOURCE, arcname="rootfs", filter=safe_root_member)
        for child in sorted(overlay.iterdir()):
            tar.add(child, arcname="rootfs/" + child.name, filter=keep)
        for child in sorted(extra.iterdir()):
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
        tar.add(ROOT / "port/patches/InfamousSecondSon.xml",
                arcname="issport/patches/InfamousSecondSon.xml")
        tar.add(ROOT / "tools/android/device/run-iss.sh", arcname="issport/run-iss.sh",
                filter=lambda info: fix_mode(info))
        tar.add(ROOT / "tools/android/runtime/launch.py", arcname="issport/launch.py")
        tar.add(GEN8 / GEN8_LIBRARY,
                arcname="rootfs/usr/lib/aarch64-linux-gnu/" + GEN8_LIBRARY)
        gen8_payload = json.loads((GEN8 / GEN8_ICD).read_text())
        if not isinstance(gen8_payload.get("ICD"), dict):
            raise SystemExit("Invalid Linux Gen8 Turnip Vulkan ICD")
        gen8_payload["ICD"]["library_path"] = APP_LOADER.replace(
            "ld-linux-aarch64.so.1", GEN8_LIBRARY)
        add_bytes(tar, "rootfs/usr/share/vulkan/icd.d/" + GEN8_ICD,
                  (json.dumps(gen8_payload, indent=2) + "\n").encode())
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
    for soname in copied:
        if "rootfs/usr/lib/aarch64-linux-gnu/" + soname not in names:
            raise SystemExit("runtime archive omitted dependency: " + soname)
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
