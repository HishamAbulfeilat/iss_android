#!/usr/bin/env bash
# Link ALL Android FFmpeg archive members into a Bionic shared library,
# catching incompatible assembly relocations before the costly Vulkan build.
set -Eeuo pipefail
cd -- "$(dirname "$0")/../../.."
ndk=${ISS_NDK:-${ANDROID_NDK_HOME:?Set ISS_NDK}}
api=${ISS_BIONIC_API:-30}
clang="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android$api-clang"
readelf="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
prefix=$PWD/.local-deps/android/bionic-prefix
out=$PWD/.local-deps/android/bionic-build
mkdir -p "$out"
libraries=()
for name in avformat avcodec swscale swresample avutil; do
    archive="$prefix/lib/lib$name.a"
    test -s "$archive"
    libraries+=("$archive")
done
log="$out/ffmpeg-shared-pic-preflight.log"
so="$out/ffmpeg-pic-preflight.so"
# Whole-archive deliberately checks code not demanded by the present GPU
# configuration. Undefined FFMPEG external calls are allowed at this probe;
# malformed PIC relocations and other real linker errors are not.
if ! "$clang" -fPIC -shared -Wl,-z,undefs -Wl,--error-limit=0 \
    -Wl,--whole-archive "${libraries[@]}" -Wl,--no-whole-archive \
    -lm -ldl -llog -lz -o "$so" >"$log" 2>&1; then
    echo "FFmpeg archive not linkable as Bionic shared library; full errors in $log" >&2
    grep -E "ld.lld: error:|recompile with -fPIC|>>> defined|>>> referenced" "$log" | head -140 >&2 || :
    tail -45 "$log" >&2
    exit 1
fi
if "$readelf" -d "$so" | grep -E 'TEXTREL|FLAGS.*TEXTREL'; then
    echo "FFmpeg has forbidden dynamic text relocations" >&2
    exit 1
fi
"$readelf" -h "$so" | grep -q 'Machine:.*AArch64'
echo "FFmpeg ARM64 PIC preflight passed: all 5 complete static archives link, no TEXTREL."
