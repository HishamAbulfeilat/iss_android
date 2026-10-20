# Building and publishing v0.1

The `experimental-bionic` branch contains the Android Bionic release source. Its existing name and workflow filename are retained to preserve build/signing continuity. Pushing the branch runs the native release workflow; publication is manual.

## Build

Run `.github/workflows/bionic-experimental.yml` from this branch. It compiles Java, runs the runtime/Vulkan integration tests, builds ARM64 FEX/Vulkan/HLE/Bloodframe/Python, verifies native payloads and signs the APK.

The existing `.local-deps/android/bionic-development.keystore` and alias are retained. The workflow restores the original signing cache (`iss-bionic-development-signing-v1`). Release builds require that key and compare the final APK certificate with `tools/android/bionic/signing-certificate.sha256`, taken from Build 65. A missing or different key fails the build instead of publishing an APK that cannot update previous installations. The key is not included in artifacts.

The app reports version `0.1`, uses version code `7` (greater than Build 65's `6`) and retains package `com.daedalus.iss.bionic`. Do not lower the version code in future releases. For local development only, `ISS_RELEASE_BUILD=0` allows the existing development-key generation path; such output does not pass the release certificate checks.

## Prepared assets

The `iss-android-bionic-apk-<commit>` Actions artifact contains:

- `inFAMOUS-Second-Son-Daedalus-v0.1.apk`
- `inFAMOUS-Second-Son-Daedalus-v0.1.apk.sha256` (relative filename, usable with `sha256sum -c`)
- `RELEASE_NOTES.md`
- `RELEASE-METADATA.json` (source commit, version, APK/runtime hashes and signing certificate)
- `LICENSE.txt`

`prepare_release.py` verifies the signed APK's actual version/package, original signing certificate, embedded runtime hash and embedded source/version metadata before writing the release assets. Artifact retention is 90 days; a published GitHub release retains its attached assets independently.

## Publish when ready

1. Use the successful build's **exact commit** from `RELEASE-METADATA.json` as the target for tag `v0.1`. Do not select an older commit from the historical glibc default branch.
2. Create a GitHub release titled `inFAMOUS Second Son — Daedalus Fork v0.1`.
3. Paste `RELEASE_NOTES.md` into the description and attach the APK and its SHA-256 file. Attach `RELEASE-METADATA.json` and `LICENSE.txt` as well if desired.
4. Review the assets and notes, then publish. The build workflow does not create tags or publish automatically.

Compilation and automated checks verify the package and integration. They do not establish a new device FPS measurement or confirm the visual effect of the upstream particle correction. Keep this distinction in the public release notes.
