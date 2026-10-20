# Android high-FPS presentation

The Android FPS selector is a presentation ceiling, not a claim of achievable performance.
The Build 62 implementation changed that ceiling but did not request a high-refresh Android
window/SurfaceControl and left the guest VBlank clock at its 60 Hz desktop default.

## Executable examined

User-supplied plaintext SELF SHA-256:
`2d1ca79630d7bbe6fa29575f59aa7996d43071d74ac8e4f7d40137967d9039ab`.
Decoded locally with the existing `prepare.parse_self`; the original file is unchanged.
The executable PT_LOAD has file offset 0x4000 and virtual address 0.

| Evidence | Decoded ELF file offset | Image-relative address |
| --- | --- | --- |
| Conditional call to sceVideoOutSetFlipRate(1) during video setup | 0x1f3e0b | 0x1efe0b |
| Runtime branch selecting flip rate 0 or 1 | 0x1f6e80 | 0x1f2e80 |
| Setter selecting flip rate 0 or 1 | 0x1f6eb0 | 0x1f2eb0 |
| Flip-status polling followed by sceKernelUsleep(1) | 0x1f4f12 | 0x1f0f12 |
| Pending-flip polling | 0x1f67ff | 0x1f27ff |

The import slot for sceVideoOutSetFlipRate is at image-relative 0xd9e4d0.
The guest has a 0/1 flip-divider path and waits for flips/buffer reuse. Its analyzed imports
contain no direct sceVideoOutWaitVblank call. Thus a 60 Hz VBlank is a ceiling when the
nonzero divider path is active, but is not by itself proof of a universal 60 FPS engine cap.
Constants such as 1/60 also occur in unrelated simulation/math code; they are not patched.
No speculative executable patch is applied.

## Changes

* For an active Android performance.state, update the VBlank timer live to
  max(60, requested_fps * (guest_flip_rate + 1)). The existing presentation ceiling
  still limits actual flips to 30/60/120/240. Other platforms keep their timing.
* Make the cross-thread flip divider atomic. Record the selected ceiling, actual
  VBlank rate and guest divider in the runtime log.
* Select the lowest same-resolution Android display mode meeting the requested rate,
  or the fastest available mode if none can meet it. Include the Bloodframe multiplier
  when requesting display refresh. Do not claim a 240 Hz display on a 120/144 Hz device.
* Request that rate on the window, Surface and the actual child ASurfaceControl;
  the latter uses the optional Android 30 ASurfaceTransaction_setFrameRate entry point.
* Keep real elapsed time/TSC and all executable constants intact.

Policy tests cover all four FPS ceilings with guest dividers 0, 1 and 2, including
120 FPS/240 Hz and 240 FPS/480 Hz pacing with divider 1. Java compilation and the full
native Android build validate integration. Device gameplay remains required to confirm
achievable source/presented FPS, Android mode selection and simulation speed. Frame
logs identify whether a remaining ceiling is at guest submission or display presentation.

Android API reference:
https://developer.android.com/media/optimize/performance/frame-rate
https://developer.android.com/ndk/reference/group/native-activity
