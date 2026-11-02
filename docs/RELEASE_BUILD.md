# Building and publishing Android releases

The `ccr-4486da53-6f6y3a` branch contains the Android Bionic release source. Pushing code changes to it runs the fork APK workflow, which builds, signs, verifies, and publishes a GitHub Release. The workflow retains its existing name and signing cache for continuity.

## Build

The `.github/workflows/fork-apk-release.yml` workflow compiles Java, runs the runtime/Vulkan integration tests, builds ARM64 FEX/Vulkan/HLE/Bloodframe/Python, verifies native payloads, signs the APK and publishes the release.

The workflow restores the fork's `.local-deps/android/bionic-development.keystore` from the `iss-fork-signing-v1` Actions cache, or initializes it from `FORK_KEYSTORE_B64` when configured. The key is not included in the APK assets. Keep the same signing key and increment both the version and version code for each release so existing installs can update.

The app retains package `com.daedalus.iss.bionic`. The build verifies the APK package, version, signer, embedded runtime and SHA-256 before publication.

## Prepared assets

The `iss-android-bionic-apk-<commit>` Actions artifact contains:

- `inFAMOUS-Second-Son-Daedalus-v<version>.apk`
- `inFAMOUS-Second-Son-Daedalus-v<version>.apk.sha256` (relative filename, usable with `sha256sum -c`)
- `RELEASE_NOTES.md`
- `RELEASE-METADATA.json` (source commit, version, APK/runtime hashes and signing certificate)
- `LICENSE.txt`

`prepare_release.py` verifies the signed APK's actual version/package, original signing certificate, embedded runtime hash and embedded source/version metadata before writing the release assets. Artifact retention is 90 days; a published GitHub release retains its attached assets independently.

Compilation and automated checks verify the package and integration. They do not establish a new device FPS measurement, thermal result, or gameplay stability. Keep this distinction in public release notes.
