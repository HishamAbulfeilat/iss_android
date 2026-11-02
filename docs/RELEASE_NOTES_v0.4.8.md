# inFAMOUS Second Son — Daedalus Android v0.4.8

## Lossless Scaling frame generation restored

- Restored the Bloodframe frame-generation runtime to the Android package.
- The build uses the pinned Bloodframe source when available. If its DXBC submodule cannot be fetched, it restores the library and all accompanying licenses from the checksum-pinned v0.4.6 release runtime.
- The APK build now fails validation if the required ARM64 frame-generation library is missing.

The checksum-pinned fallback is the same library shipped in v0.4.6, whose release notes reported 30 real FPS and 60 presented FPS with Bloodframe in the tested scene. Actual results vary by game scene and device; frame generation, stability, thermals, and memory use still require testing on the Galaxy S25 Ultra.
