#!/system/bin/sh
# Experimental inFAMOUS Second Son on Android Bionic. No Debian/Xvfb/glibc.
set -eu
files=${FXD_FILES:?ISS Bionic requires the app private data directory}
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
# Preserve the existing Android AudioTrack FIFO path used by the glibc build.
# The Bionic probe links SDL3 statically; 'disk' routes its PCM to AudioBridge.
export SDL_AUDIO_DRIVER=disk SDL_AUDIODRIVER=disk
export SDL_AUDIO_DISK_OUTPUT_FILE="$base/audio.fifo" SDL_AUDIO_DISK_TIMESCALE=0
export SDL_AUDIO_FORMAT=S16LE SDL_AUDIO_CHANNELS=2 SDL_AUDIO_FREQUENCY=48000
export TLG_PAD_QUIET=1
export ISS_PRESENT_SOCKET="$base/present.sock"
export ISS_SCREEN_W="${ISS_SCREEN_W:-1280}" ISS_SCREEN_H="${ISS_SCREEN_H:-720}"
mkdir -p "$TMPDIR" "$HOME/.fex-emu" "$HOME/.cache/fex-emu" "$base/user" "$base/out"
[ -x "$python" ] || { echo "Bionic Python executable missing" >&2; exit 127; }
[ -x "$probe" ] || { echo "Bionic inFAMOUS probe missing" >&2; exit 127; }
chip=$("$runtime/bin/kgsl-chip-id" 2>/dev/null || true)
# Redmagic / Adreno Gen8 builds explicitly prefer the pinned V37 Patched HAL.
# A user can select the stock loader or enable KGSL-based detection in the UI.
case "${ISS_BIONIC_DRIVER:-gen8}" in
 gen8) export ISS_VULKAN_DRIVER="$runtime/drivers/gen8/libvulkan_freedreno.so" ;;
 system) unset ISS_VULKAN_DRIVER ;;
 auto) case "$chip" in
   0x44??????) export ISS_VULKAN_DRIVER="$runtime/drivers/gen8/libvulkan_freedreno.so" ;;
   *) unset ISS_VULKAN_DRIVER ;;
  esac ;;
 *) echo "Invalid Bionic Vulkan driver selection" >&2; exit 4 ;;
esac
if [ -n "${ISS_VULKAN_DRIVER:-}" ]; then
 [ -s "$ISS_VULKAN_DRIVER" ] || { echo "Selected Bionic Vulkan driver missing: $ISS_VULKAN_DRIVER" >&2; exit 4; }
 echo "ISS Bionic driver: $ISS_VULKAN_DRIVER, chip=$chip"
else
 echo "ISS Bionic Vulkan: Android system loader, chip=$chip"
fi
echo "ISS Android Bionic: game=$TLG_GAME_DIR, output=${ISS_SCREEN_W}x${ISS_SCREEN_H}"
exec "$python" "$base/launch.py"
