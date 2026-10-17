#!/system/bin/sh
# Run the inFAMOUS PC port from Android's app-private glibc runtime.
set -eu
: "${FXD_FILES:?}"
: "${FXD_ROOT:?}"
: "${ISS_GAME_DIR:?}"
root="$FXD_ROOT"
base="$FXD_FILES/issport"
loader="$root/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1"
game="$ISS_GAME_DIR"
out="$base/out"
mkdir -p "$out" "$base/user"
cd "$base"
export HOME="$FXD_FILES/home/iss"
export PYTHONHOME="$root/usr"
export PYTHONIOENCODING=utf-8
export TLG_GAME_DIR="$game"
export TLG_DATA_DIR="$base"
export TLG_USER_DIR="$base/user"
export TLG_CONFIG="$base/tlg.ini"
export LD_LIBRARY_PATH="$base/lib:$root/usr/lib/aarch64-linux-gnu:$root/usr/lib"
export DISPLAY=:0
export SDL_VIDEODRIVER=x11
export SDL_AUDIODRIVER=disk
export SDL_DISKAUDIOFILE="$base/audio.fifo"
export SDL_DISKAUDIOFORMAT=S16LE
export ISS_ANDROID_INPUT=1
export VK_LOADER_LAYERS_DISABLE='~implicit~'
# The bundled fexdroid Turnip/loader must resolve inside this app, not /usr on Android.
if [ -f "$root/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json" ]; then
  export VK_DRIVER_FILES="$root/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json"
fi
python="$root/usr/bin/python3.13"
[ -f "$python" ] || { echo "Android runtime missing Python 3.13" >&2; exit 31; }
[ -f "$game/eboot.bin" ] || { echo "Game eboot.bin is not readable" >&2; exit 32; }
# The existing iss_pc scripts operate on the user's own decrypted/valid game dump.
"$loader" "$python" "$base/scripts/prepare.py" "$game" --out "$out"
"$loader" "$python" "$base/scripts/link_libc.py" "$game" --out "$out"
"$loader" "$python" "$base/scripts/link_modules.py" "$game" --out "$out"
"$loader" "$python" "$base/scripts/content_profile.py" "$game" --out "$out" --sku full
"$loader" "$python" "$base/scripts/patches.py" --out "$out" --extra "" --game-dir "$game" \
  --patches-dir "$base/patches" --patches-config "$base/patches.json"
[ -f "$out/boot-linked.bin" ] || { echo "Game module linking did not produce boot-linked.bin" >&2; exit 33; }
exec "$loader" "$base/bin/iss-probe" "$out/boot-linked.bin" \
  --content-profile "$out/content.bin" --patches "$out/patches.bin" \
  --app0 "$game" --user "$base/user" --timeout 0
