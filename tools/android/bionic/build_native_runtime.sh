#!/usr/bin/env bash
# Build the actual inFAMOUS Bionic game loader and Android direct-present JNI.
set -Eeuo pipefail
trap 'echo "ISS Bionic native runtime failed at $LINENO: $BASH_COMMAND" >&2' ERR
cd -- "$(dirname "$0")/../../.."
ndk=${ISS_NDK:-${ANDROID_NDK_HOME:?Set ISS_NDK}}
api=${ISS_BIONIC_API:-30}
llvm=$ndk/toolchains/llvm/prebuilt/linux-x86_64
prefix=$PWD/.local-deps/android/bionic-prefix
out=$PWD/out/bionic
cc="$llvm/bin/aarch64-linux-android${api}-clang"
ar="$llvm/bin/llvm-ar"
test -s "$out/lib/libisscpu.so"
test -s "$out/lib/libtlggpu.so"
test -s "$prefix/lib/libavcodec.a"
test -s "$prefix/lib/libSDL3.a"
test -s "$prefix/lib/libfreetype.a"
mkdir -p "$out/bin" "$out/atrac9" "$out/runtime-objects" "$out/lib"
for source in port/third_party/LibAtrac9/C/src/*.c; do
  "$cc" -std=c99 -O2 -w -fPIC -c "$source" -o "$out/atrac9/$(basename "${source%.c}").o"
done
"$ar" rcs "$out/libatrac9.a" "$out"/atrac9/*.o
includes=(-Iport -Iport/src -Iport/gpu -I"$prefix/include" -I"$prefix/include/freetype2")
objects=()
for source in port/src/probe.c port/src/runtime*.c; do
  object="$out/runtime-objects/$(basename "${source%.c}").o"
  "$cc" -std=gnu11 -O2 -g -Wall -Wextra -pthread -fPIE "${includes[@]}" -c "$source" -o "$object"
  objects+=("$object")
done
# The Vulkan test in the desktop probe calls the system Vulkan loader directly.
# Bionic must instead always use the configured Turnip HAL through the ISS Vulkan driver shim.
cat > "$out/runtime-objects/vulkan_smoke_bionic.c" <<'EOF'
#include <stdio.h>
int vulkan_smoke(void) {
  fputs("Use the ISS Bionic Vulkan HAL smoke path; the desktop Vulkan test is not available.\n", stderr);
  return 1;
}
EOF
"$cc" -std=c11 -O2 -c "$out/runtime-objects/vulkan_smoke_bionic.c" -o "$out/runtime-objects/vulkan_smoke_bionic.o"
"$cc" -pie -rdynamic "${objects[@]}" "$out/runtime-objects/vulkan_smoke_bionic.o" \
  "$out/libatrac9.a" \
  "$prefix/lib/libavformat.a" "$prefix/lib/libavcodec.a" "$prefix/lib/libswscale.a" \
  "$prefix/lib/libswresample.a" "$prefix/lib/libavutil.a" \
  "$prefix/lib/libSDL3.a" "$prefix/lib/libfreetype.a" \
  -L"$out/lib" -ltlggpu -lisscpu -lc++_shared -lm -lz -ldl -landroid -llog \
  -lOpenSLES -lEGL -lGLESv2 -pthread -Wl,-rpath,'$ORIGIN/../lib' \
  -o "$out/bin/iss-probe"
"$cc" -std=gnu11 -O2 -Wall -Wextra -pie tools/android/build/kgsl_chip_id.c -o "$out/bin/kgsl-chip-id"
"$cc" -std=gnu11 -O2 -Wall -Wextra -fPIC -shared -Itools/android/present \
  tools/android/bionic/present/isspresent.c -landroid -lnativewindow -llog -ldl -pthread \
  -o "$out/lib/libisspresent.so"
for file in "$out/bin/iss-probe" "$out/bin/kgsl-chip-id" "$out/lib/libisspresent.so"; do
  "$llvm/bin/llvm-readelf" -h "$file" | grep -q 'Machine:.*AArch64'
done
echo "Compiled the inFAMOUS Bionic probe, KGSL helper and direct AHardwareBuffer bridge."
