#!/usr/bin/env bash
# Native Android Bionic FEX; glibc and the existing runtime are never packaged.
set -Eeuo pipefail
cd -- "$(dirname "$0")/../../.."
ndk=${ISS_NDK:-${ANDROID_NDK_HOME:?Set ISS_NDK or ANDROID_NDK_HOME}}
api=${ISS_BIONIC_API:-30}
jobs=${ISS_JOBS:-2}
unset CFLAGS CXXFLAGS CPPFLAGS LDFLAGS CPATH C_INCLUDE_PATH CPLUS_INCLUDE_PATH LIBRARY_PATH CMAKE_PREFIX_PATH
cmake -S third_party/FEX -B out/bionic/fex -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-$api -DANDROID_STL=c++_shared \
    -DCMAKE_BUILD_TYPE=Release -DCMAKE_PROJECT_FEX_INCLUDE="$PWD/cpu/fex_project.cmake" \
    -DBUILD_TESTING=OFF -DBUILD_THUNKS=OFF -DBUILD_FEXCONFIG=OFF -DBUILD_STEAM_SUPPORT=OFF \
    -DBUILD_FEX_LINUX_TESTS=OFF -DENABLE_ZYDIS=OFF -DTUNE_ARCH=armv8.2-a -DTUNE_CPU=generic \
    -DENABLE_JEMALLOC_GLIBC_ALLOC=OFF
cmake --build out/bionic/fex --target isscpu --parallel "$jobs"
mkdir -p out/bionic/lib
cp out/bionic/fex/libisscpu.so out/bionic/lib/
cp "$ndk/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so" out/bionic/lib/
