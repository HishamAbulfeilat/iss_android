#!/system/bin/sh
set -eu
files=${FXD_FILES:?};root="$files/rootfs";base="$files/issport"
loader="$root/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1"
export LD_LIBRARY_PATH="$base/lib:$root/usr/lib/aarch64-linux-gnu:$root/usr/lib"
export HOME="$files/home/iss" TMPDIR="$root/tmp" XDG_RUNTIME_DIR="$root/tmp"
export FXD_ROOT="$root" FXD_SHM_SOCKET="$root/tmp/.fxshm/sock" FEXDROID_PRESENT="$root/tmp/fxpresent.sock"
export DISPLAY=:0 SDL_VIDEO_DRIVER=x11 PYTHONHOME="$root/usr" PYTHONNOUSERSITE=1
export TLG_CONFIG="$base/iss.ini" TLG_DATA_DIR="$base/data" TLG_GAME_DIR="${ISS_GAME_DIR:?}"
export TLG_PAD_FILE="$base/android-pad.state" TLG_PAD_QUIET=1 TLG_ANDROID_INPUT=1
export SDL_AUDIO_DRIVER=disk SDL_AUDIO_DISK_OUTPUT_FILE="$base/audio.fifo" SDL_AUDIO_DISK_TIMESCALE=0 SDL_AUDIO_DEVICE_SAMPLE_FRAMES=512
export TLG_APP_NAME="inFAMOUS Second Son" TLG_APP_ID=com.daedalus.iss
export TLG_UPSCALER=none TLG_READBACK_LINEAR=1 TLG_READBACKS=2 TLG_VBLANK_HZ=60
icd="$root/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json"
case $("$loader" "$base/bin/kgsl-chip-id" 2>/dev/null || true) in
 0x44*) [ ! -f "$root/usr/share/vulkan/icd.d/freedreno_gen8_icd.aarch64.json" ] || icd="$root/usr/share/vulkan/icd.d/freedreno_gen8_icd.aarch64.json" ;;
esac
export VK_DRIVER_FILES="$icd" VK_ICD_FILENAMES="$icd"
mkdir -p "$TLG_DATA_DIR/out" "$HOME"
cd "$base"
exec "$loader" "$root/usr/bin/python3.13" "$base/launch.py"
