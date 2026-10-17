#!/usr/bin/env bash
# Audit PIC relocations in every FFmpeg ARM64 static library BEFORE the
# expensive native GPU build. Test each archive independently: FFmpeg intentionally
# contains same-named implementation symbols in DIFFERENT libraries; forcing
# all five archives at once introduces duplicate-symbol errors that the normal
# linker (selective archive extraction) will never encounter.
set -Eeuo pipefail
cd -- "$(dirname "$0")/../../.."
ndk=${ISS_NDK:-${ANDROID_NDK_HOME:?Set ISS_NDK}}
api=${ISS_BIONIC_API:-30}
clang="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android${api}-clang"
readelf="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
prefix=$PWD/.local-deps/android/bionic-prefix
out=$PWD/.local-deps/android/bionic-build
mkdir -p "$out"
failed=0
for name in avformat avcodec swscale swresample avutil; do
    archive="$prefix/lib/lib${name}.a"
    log="$out/ffmpeg-pic-${name}.log"
    so="$out/ffmpeg-pic-${name}.so"
    if [[ ! -s "$archive" ]]; then
        echo "FFmpeg PIC preflight: missing $archive" >&2
        failed=1
        continue
    fi
    # Whole-archive on JUST ONE library exercises every .o and every NEON
    # specialization. Shared undefined references into sibling libraries are
    # intentionally allowed here; they will be checked by the real GPU link.
    # It is WRONG to combine multiple FFmpeg archives under --whole-archive:
    # e.g. ff_init_half2float_tables has an implementation in both avcodec and
    # swscale, and regular links extract only the needed definition.
    if ! "$clang" -fPIC -shared -Wl,-z,undefs -Wl,--error-limit=0 \
        -Wl,--whole-archive "$archive" -Wl,--no-whole-archive \
        -lm -ldl -llog -lz -o "$so" >"$log" 2>&1; then
        echo "FFmpeg PIC preflight FAILED: $name (full log: $log)" >&2
        grep -E 'ld.lld: error:|recompile with -fPIC|>>> defined|>>> referenced' "$log" | head -120 >&2 || :
        tail -35 "$log" >&2
        failed=1
        continue
    fi
    if "$readelf" -d "$so" | grep -E 'TEXTREL|FLAGS.*TEXTREL'; then
        echo "FFmpeg PIC preflight FAILED: $name has forbidden TEXTREL" >&2
        failed=1
        continue
    fi
    "$readelf" -h "$so" | grep -q 'Machine:.*AArch64'
    echo "FFmpeg PIC preflight OK: $name, all static members link without TEXTREL"
done
if [[ "$failed" != 0 ]]; then
    echo "FFmpeg PIC preflight found one or more invalid ARM64 archives; full reports above" >&2
    exit 1
fi
echo "FFmpeg ARM64 PIC preflight PASS: 5 independently validated static archives."
