#!/usr/bin/env bash
# Build ISS's OWN Vulkan/HLE code; GoW3/Bloodborne supply the Android backend only.
set -Eeuo pipefail
cd -- "$(dirname "$0")/../../.."
ndk=${ISS_NDK:-${ANDROID_NDK_HOME:?Set ISS_NDK or ANDROID_NDK_HOME}}
api=${ISS_BIONIC_API:-30}
jobs=${ISS_JOBS:-2}
prefix=$PWD/.local-deps/android/bionic-prefix
headers=${ISS_BIONIC_HEADERS:-$PWD/.local-deps/root/usr}
build=$PWD/out/bionic/gpu
[[ -f "$prefix/.done-ffmpeg" ]] || { echo "Run tools/android/bionic/build_deps.sh" >&2; exit 1; }
unset CFLAGS CXXFLAGS CPPFLAGS LDFLAGS CPATH C_INCLUDE_PATH CPLUS_INCLUDE_PATH LIBRARY_PATH CMAKE_PREFIX_PATH
export PATH="$headers/bin:$PATH"
export PKG_CONFIG_LIBDIR="$prefix/lib/pkgconfig:$prefix/share/pkgconfig"
export PKG_CONFIG_PATH="$PKG_CONFIG_LIBDIR"
mkdir -p "$build/.cmake/api/v1/query"
touch "$build/.cmake/api/v1/query/codemodel-v2"
cmake -S port/gpu -B "$build" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-$api -DANDROID_STL=c++_shared \
  -DCMAKE_BUILD_TYPE=RelWithDebInfo -DISS_ANDROID_BIONIC=ON -DTLG_LTO=OFF -DTLG_PGO=off \
  -DCMAKE_FIND_ROOT_PATH="$prefix" -DCMAKE_FIND_ROOT_PATH_MODE_PACKAGE=BOTH \
  -DCMAKE_FIND_ROOT_PATH_MODE_LIBRARY=ONLY -DCMAKE_FIND_ROOT_PATH_MODE_INCLUDE=BOTH \
  "-DCMAKE_PREFIX_PATH=$prefix;$headers" \
  -Dmagic_enum_DIR="$headers/share/cmake/magic_enum" \
  -Dtsl-robin-map_DIR="$headers/share/cmake/tsl-robin-map" \
  -DVulkanMemoryAllocator_DIR="$headers/share/cmake/VulkanMemoryAllocator" \
  -DBoost_DIR="$headers/lib/cmake/Boost-1.92.0" \
  -Dboost_headers_DIR="$headers/lib/cmake/boost_headers-1.92.0"
python3 tools/android/bionic/audit_link.py --build "$build" --prefix "$prefix" --ndk "$ndk"
if [[ ${1:-} = --configure-only ]]; then exit 0; fi
# Collect all further compile failures during one CI run instead of stopping at first object.
cmake --build "$build" --target tlggpu --parallel "$jobs" -- -k 0
mkdir -p out/bionic/lib
cp "$build/libtlggpu.so" out/bionic/lib/
