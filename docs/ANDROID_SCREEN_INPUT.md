# Android screen / input regressions

The graffiti help overlay is an SDL input modal. Android TouchControllerOverlay
writes `android-pad.state`, not SDL keyboard events. The Vulkan overlay now
checks the same state for a **new** cross/circle/options press while visible,
closing the modal without faking gamepad events. A short-lived
`android-pad.state.guide` marker also permits pointer clicks on the real
"Got it" button without turning touchscreen taps into in-game R2 input.

The app's launcher offers a real **FPS HUD** (the native presenter timing),
`Fit 16:9` vs `Fill`, and 720p/1080p **output resolution**. Fit/Fill
affects only the Android SurfaceView (Xvfb remains 1280x720); output resolution
affects the Vulkan output target and must **not** be confused with game-internal
resolution patches. The latter require CUSA00004 01.00 offsets and signatures.

The recorded FPS oscillates from ~60 in lighter scenes to ~24 in heavy scenes.
A 12.3-second freeze recovered and texture allocation reached ~6 GiB against a
~3 GiB configured budget. No assumption is made that a display mode or FPS HUD
will fix these independent GPU memory / frame pacing problems.
