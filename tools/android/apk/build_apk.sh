#!/usr/bin/env bash
# Build/installable *experimental* arm64 APK, signed with a local development key.
set -Eeuo pipefail
trap 'echo "build_apk failed at line $LINENO: $BASH_COMMAND" >&2' ERR
cd -- "$(dirname -- "$0")/../../.."
sdk=${ANDROID_SDK_ROOT:-${ANDROID_HOME:?Android SDK is not configured}}
bt="$sdk/build-tools/35.0.0"
jar="$sdk/platforms/android-35/android.jar"
for tool in aapt2 d8 zipalign apksigner; do
  [[ -x "$bt/$tool" ]] || { echo "Missing Android build tool: $tool" >&2; exit 1; }
done
[[ -f "$jar" ]] || { echo "Missing android-35 platform" >&2; exit 1; }
python3 tools/android/build/build_bundle.py
python3 tools/android/runtime/package_runtime.py
work="$PWD/out/android-apk"
mkdir -p "$work/classes" "$work/package/assets" "$work/package/lib/arm64-v8a" "$PWD/out/release"
javac -encoding UTF-8 -source 8 -target 8 -cp "$jar" -d "$work/classes" \
  android/app/src/ro/cobrabm/fexdroid/*.java
mapfile -d '' classes < <(find "$work/classes" -name '*.class' -print0)
[[ ${#classes[@]} -gt 0 ]] || { echo 'No compiled launcher classes' >&2; exit 1; }
"$bt/d8" --min-api 28 --lib "$jar" --output "$work/package" "${classes[@]}"
cp out/release/runtime.tar.gz out/release/runtime.sha256 "$work/package/assets/"
cp android/app/jni/arm64-v8a/libfxdisplay.so android/app/jni/arm64-v8a/libfxio.so \
   "$work/package/lib/arm64-v8a/"
"$bt/aapt2" link -I "$jar" --manifest android/app/AndroidManifest.xml \
  --min-sdk-version 28 --target-sdk-version 28 -o "$work/base.apk"
(cd "$work/package"; zip -q -0 -r "$work/base.apk" assets lib classes.dex)
"$bt/zipalign" -f 4 "$work/base.apk" "$work/aligned.apk"
key="$work/development.keystore"
if [[ ! -f $key ]]; then
  keytool -genkeypair -keystore "$key" -storepass android -keypass android \
    -alias iss-dev -dname "CN=ISS Android Experimental" -keyalg RSA -keysize 2048 \
    -validity 10000 -noprompt >/dev/null 2>&1
fi
apk="out/release/iss-android-glibc-experimental.apk"
"$bt/apksigner" sign --ks "$key" --ks-key-alias iss-dev \
  --ks-pass pass:android --key-pass pass:android --out "$apk" "$work/aligned.apk"
"$bt/apksigner" verify --verbose "$apk"
"$bt/aapt" dump badging "$apk" | grep -F "package: name='com.daedalus.iss'"
unzip -Z1 "$apk" | grep -qx 'assets/runtime.tar.gz'
unzip -Z1 "$apk" | grep -qx 'assets/runtime.sha256'
unzip -Z1 "$apk" | grep -qx 'lib/arm64-v8a/libfxdisplay.so'
unzip -Z1 "$apk" | grep -qx 'lib/arm64-v8a/libfxio.so'
sha256sum "$apk" > "$apk.sha256"
echo "Experimental, untested on device: $apk"
