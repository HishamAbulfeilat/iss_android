#!/usr/bin/env bash
# Verify that the actual FFmpeg dependency group, including the original
# troublesome NEON FFT assembly object, links into an Android Bionic shared ELF.
# IMPORTANT: Do not --whole-archive FFmpeg: several archives intentionally
# contain same-named symbols; forcing everything in creates false failures.
set -Eeuo pipefail
cd -- "$(dirname "$0")/../../.."
ndk=${ISS_NDK:-${ANDROID_NDK_HOME:?Set ISS_NDK}}
api=${ISS_BIONIC_API:-30}
clang="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android${api}-clang"
readelf="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
prefix=$PWD/.local-deps/android/bionic-prefix
out=$PWD/.local-deps/android/bionic-build
mkdir -p "$out"
log="$out/ffmpeg-shared-pic-integration.log"
so="$out/ffmpeg-pic-integration.so"
map="$out/ffmpeg-pic-integration.map"
src="$out/ffmpeg-pic-integration.c"
for name in avformat avcodec swscale swresample avutil; do
    test -s "$prefix/lib/lib${name}.a"
done
cat >"$src" <<'EOF'
#include <libavutil/tx.h>
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libswscale/swscale.h>
#include <libswresample/swresample.h>

/* Force exactly the tx_float_neon.o that failed in the GPU build.
 * This is a link-only fixture: the internal function is NEVER called.
 * ff_tx_fft_sr_float_neon exists in pinned FFmpeg 7.1.5. */
extern void ff_tx_fft_sr_float_neon(void);
__attribute__((visibility("default"), used))
void (*iss_ffmpeg_neon_pic_reference)(void) = ff_tx_fft_sr_float_neon;

/* Pull the real public FFmpeg entry points and their static dependencies. */
__attribute__((visibility("default")))
unsigned iss_ffmpeg_link_probe(void) {
    AVTXContext *ctx = NULL;
    av_tx_fn transform = NULL;
    int error = av_tx_init(&ctx, &transform, AV_TX_FLOAT_FFT, 0, 32, NULL, 0);
    if (ctx) av_tx_uninit(&ctx);
    return (unsigned)error + avformat_version() + avcodec_version() +
           swscale_version() + swresample_version() + avutil_version();
}
EOF
# -z undefs mirrors tlggpu's handling of runtime-provided symbols.
# --start-group preserves FFmpeg's NORMAL selective archive extraction and
# resolves cross-library dependencies; no duplicate-symbol false positives.
if ! "$clang" -std=c11 -fPIC -shared -I"$prefix/include" \
    -Wl,-z,undefs -Wl,--error-limit=0 "-Wl,-Map=$map" \
    "$src" -Wl,--start-group \
    "$prefix/lib/libavformat.a" "$prefix/lib/libavcodec.a" \
    "$prefix/lib/libswscale.a" "$prefix/lib/libswresample.a" \
    "$prefix/lib/libavutil.a" -Wl,--end-group \
    -lm -ldl -llog -lz -o "$so" >"$log" 2>&1; then
    echo "FFmpeg realistic Bionic PIC integration FAILED ($log)" >&2
    grep -E 'ld.lld: error:|recompile with -fPIC|>>> defined|>>> referenced|error:' "$log" | head -130 >&2 || :
    tail -55 "$log" >&2
    exit 1
fi
if ! grep -Fq 'tx_float_neon.o' "$map"; then
    echo "FFmpeg PIC integration did not reach tx_float_neon.o; test is insufficient" >&2
    exit 1
fi
if "$readelf" -d "$so" | grep -E 'TEXTREL|FLAGS.*TEXTREL'; then
    echo "FFmpeg PIC integration contains Android-forbidden TEXTREL" >&2
    exit 1
fi
"$readelf" -h "$so" | grep -q 'Machine:.*AArch64'
echo "FFmpeg Android PIC link PASS: actual dependency group, NEON FFT included, no text relocations."
