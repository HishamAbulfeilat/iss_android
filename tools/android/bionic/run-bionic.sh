#!/system/bin/sh
# Experimental inFAMOUS Second Son on Android Bionic. No Debian/Xvfb/glibc.
set -eu
files=${FXD_FILES:?ISS Bionic requires the application's private data directory}
base="$files/issport"
runtime="$base/arm64"
python="$runtime/python/bin/python3.13"
probe="$runtime/bin/iss-probe"
export HOME="$files/home/iss" TMPDIR="$base/tmp" XDG_RUNTIME_DIR="$base/tmp"
export PATH=/system/bin
export LD_LIBRARY_PATH="$runtime/lib:$runtime/python/lib"
export PYTHONHOME="$runtime/python" PYTHONPATH="$base/scripts" PYTHONUTF8=1
export FEX_APP_CONFIG_LOCATION="$HOME/.fex-emu/" FEX_APP_DATA_LOCATION="$HOME/.fex-emu/"
export FEX_APP_CACHE_LOCATION="$HOME/.cache/fex-emu"
export TLG_DATA_DIR="$base" TLG_GAME_DIR="${ISS_GAME_DIR:?Choose the extracted inFAMOUS game folder}"
export ISS_BIONIC=1 ISS_ANDROID_INPUT=1 TLG_ANDROID_INPUT=1
export TLG_PAD_FILE="$base/android-pad.state"
export TLG_CONFIG="$base/user/tlg.ini" ISS_CONFIG="$base/user/tlg.ini"
export TLG_GPU_USER_DIR="$base/user"
export TLG_AUDIO_FIFO="$base/audio.fifo"
export ISS_PRESENT_SOCKET="$base/present.sock"
export ISS_SCREEN_W="${ISS_SCREEN_W:-1280}" ISS_SCREEN_H="${ISS_SCREEN_H:-720}"
mkdir -p "$TMPDIR" "$HOME/.fex-emu" "$HOME/.cache/fex-emu" "$base/user" "$base/out"
[ -x "$python" ] || { echo "Bionic Python executable missing" >&2; exit 127; }
[ -x "$probe" ] || { echo "Bionic inFAMOUS probe missing" >&2; exit 127; }
chip=$("$runtime/bin/kgsl-chip-id" 2>/dev/null || true)
if [ "${ISS_BIONIC_DRIVER:-auto}" = "auto" ]; then
 case "$chip" in 0x44??????) export ISS_VULKAN_DRIVER="$runtime/drivers/gen8/libvulkan_freedreno.so" ;;
 *) unset ISS_VULKAN_DRIVER ;;
 esac
fi
if [ -n "${ISS_VULKAN_DRIVER:-}" ]; then
 [ -s "$ISS_VULKAN_DRIVER" ] || { echo "Selected Bionic Vulkan driver missing: $ISS_VULKAN_DRIVER" >&2; exit 4; }
 echo "ISS Bionic driver: $ISS_VULKAN_DRIVER, chip=$chip"
else
 echo "ISS Bionic Vulkan: Android system loader, chip=$chip"
fi
echo "ISS Android Bionic: game=$TLG_GAME_DIR, output=${ISS_SCREEN_W}x${ISS_SCREEN_H}"
exec "$python" "$base/launch.py"
