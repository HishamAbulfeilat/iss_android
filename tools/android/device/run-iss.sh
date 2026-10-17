#!/system/bin/sh
# Android/Bionic shell supervising the separate glibc/FEX game process.
# Never put rootfs/usr/bin ahead of /system/bin: Android cannot exec those ELFs directly.
set -eu
export PATH=/system/bin:/system/xbin
umask 077
: "${FXD_FILES:?Missing app files directory}"
: "${FXD_ROOT:?Missing glibc rootfs}"
: "${ISS_GAME_DIR:?Missing selected game directory}"
root="$FXD_ROOT"
base="$FXD_FILES/issport"
loader="$root/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1"
python="$root/usr/bin/python3.13"
game="$ISS_GAME_DIR"
for required in "$loader" "$python" "$base/bin/iss-probe" "$base/lib/libisscpu.so" \
                "$base/lib/libtlggpu.so" "$base/launch.py" "$game/eboot.bin"; do
  if [ ! -r "$required" ]; then
    echo "ISS preflight: missing or unreadable $required" >&2
    exit 31
  fi
done
icd="$root/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json"
if [ ! -r "$icd" ]; then
  echo "ISS preflight: missing bundled Vulkan ICD $icd" >&2
  exit 32
fi
/system/bin/mkdir -p "$base/out" "$base/user" "$FXD_FILES/home/iss" || exit 33
cd "$base" || exit 34
export HOME="$FXD_FILES/home/iss"
export TMPDIR="$root/tmp" XDG_RUNTIME_DIR="$root/tmp"
export FXD_SHM_SOCKET="$root/tmp/.fxshm/sock"
export FEXDROID_PRESENT="$root/tmp/fxpresent.sock"
export PYTHONHOME="$root/usr" PYTHONNOUSERSITE=1 PYTHONIOENCODING=utf-8
export TLG_GAME_DIR="$game" TLG_DATA_DIR="$base" TLG_USER_DIR="$base/user"
export TLG_CONFIG="$base/iss.ini"
export LD_LIBRARY_PATH="$base/lib:$root/usr/lib/aarch64-linux-gnu:$root/usr/lib"
export DISPLAY=:0 SDL_VIDEODRIVER=x11
# SDL3 disk driver feeds the Android AudioTrack FIFO; do not use a host Linux sound daemon.
export SDL_AUDIODRIVER=disk SDL_DISKAUDIOFILE="$base/audio.fifo" SDL_DISKAUDIOFORMAT=S16LE
export ISS_ANDROID_INPUT=1 TLG_PAD_FILE="$base/android-pad.state" TLG_PAD_QUIET=1
export TLG_APP_NAME="inFAMOUS Second Son" TLG_APP_ID=com.daedalus.iss
export VK_DRIVER_FILES="$icd" VK_ICD_FILENAMES="$icd"
export VK_LOADER_LAYERS_DISABLE='~implicit~'
echo "ISS preflight OK: Android shell, glibc loader, FEX, Vulkan ICD and game entry readable"
echo "ISS launching upstream inFAMOUS preparation under explicit ARM64 glibc loader"
exec "$loader" "$python" "$base/launch.py"
