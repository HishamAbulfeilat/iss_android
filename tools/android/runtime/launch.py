"""Prepare the user's own inFAMOUS Second Son files and enter the ARM64/FEX loader.

All Linux executables are invoked via the explicit glibc loader: the Android kernel
does not provide /lib/ld-linux-aarch64.so.1, and its shell uses Bionic.
"""
import os
from pathlib import Path
import subprocess
import sys

BASE = Path(__file__).resolve().parent
sys.path.insert(0, str(BASE / "scripts"))

def main():
    env = os.environ
    game = Path(env["TLG_GAME_DIR"])
    data = Path(env["TLG_DATA_DIR"])
    out = data / "out"
    root = Path(env["FXD_ROOT"])
    loader = root / "usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1"
    python = root / "usr/bin/python3.13"
    for filename in ("eboot.bin", "sce_sys/param.sfo",
                     "sce_module/libc.prx", "sce_module/libSceFios2.prx"):
        file = game / filename
        if not file.is_file():
            raise SystemExit(f"ISS game preflight: required original dump file missing: {file}")
    if not (game / "dvdroot_ps4").is_dir() and not list(game.glob("*.psarc")):
        raise SystemExit("ISS game preflight: missing dvdroot_ps4/ and PSARC game resources.")
    if not loader.is_file() or not python.is_file():
        raise SystemExit("ISS runtime preflight: ARM64 loader or Python is missing.")
    out.mkdir(parents=True, exist_ok=True)
    (data / "user").mkdir(parents=True, exist_ok=True)

    import graphics_options
    settings, reverted = graphics_options.prepare(env["TLG_CONFIG"], out / "graphics_state.json")
    env.update(graphics_options.environment(settings))
    print(f"ISS graphics startup options: {settings}; reverted={reverted}", flush=True)

    def run_step(script, *arguments):
        name = BASE / "scripts" / (script + ".py")
        if not name.is_file():
            raise SystemExit(f"ISS package incomplete: missing {name}")
        print(f"ISS preparation: {script}", flush=True)
        subprocess.run([str(loader), str(python), str(name), *map(str, arguments)],
                       check=True, env=env)
        print(f"ISS preparation complete: {script}", flush=True)

    run_step("prepare", game, "--out", out)
    # link_modules uses freshly prepared boot.bin, includes libc.prx and libSceFios2.prx,
    # and produces boot-linked.bin; the legacy link_libc step is not an input to it.
    run_step("link_modules", game, "--out", out)
    run_step("content_profile", game, "--out", out, "--sku", "full")
    run_step("patches", "--out", out, "--extra", env.get("TLG_PATCHES", ""),
             "--game-dir", game, "--patches-dir", data / "patches",
             "--patches-config", data / "patches.json")
    image = out / "boot-linked.bin"
    if not image.is_file() or image.stat().st_size == 0:
        raise SystemExit("ISS preparation: boot-linked.bin was not produced.")
    for artifact in ("content.bin", "patches.bin"):
        if not (out / artifact).is_file():
            raise SystemExit(f"ISS preparation: missing {artifact}")
    args = [str(loader), str(BASE / "bin/iss-probe"), str(image),
            "--content-profile", str(out / "content.bin"),
            "--patches", str(out / "patches.bin"),
            "--app0", str(game), "--user", str(data / "user"), "--timeout", "0"]
    print("ISS preparation complete; entering ARM64 FEX game loader", flush=True)
    sys.stdout.flush()
    sys.stderr.flush()
    os.execve(str(loader), args, env)

if __name__ == "__main__":
    main()
