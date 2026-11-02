# inFAMOUS Second Son — Daedalus Android v0.4.7

## Adaptive quality and add-on support (experimental)

- Added an opt-in adaptive quality governor for the verified CUSA00223 01.07 executable. It can reduce distant detail when the guest frame rate remains below the selected FPS target; the feature is off by default and requires the expected executable hook.
- Added an opt-in Paper Trail offline patch, restricted to the verified 01.07 eboot hash. It does not provide website karma rewards.
- Added installation and runtime discovery for matching CUSA00223 add-on PKGs. Packages are staged and checked against their header and `param.sfo` metadata before installation; existing add-on folders are not replaced.

These features have focused automated coverage, but have not yet been validated in gameplay on a Galaxy S25 Ultra. Performance, graphics, thermals, memory use, and DLC behavior should be tested on-device. Adaptive quality and Paper Trail patching are opt-in.
