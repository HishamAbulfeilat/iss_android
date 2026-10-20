# inFAMOUS Second Son — Daedalus Fork 0.1

Android ARM64 port of **inFAMOUS Second Son**, based on [HayatoG/iss_pc](https://github.com/HayatoG/iss_pc), with an Android **Bionic** runtime, FEX CPU translation and native Vulkan presentation through AHardwareBuffer.

Fork and Android adaptations: **erickmendes22 / [Daedalus Emulator](https://www.youtube.com/@daedalusemulator)**.

## Download and requirements

Download `inFAMOUS-Second-Son-Daedalus-v0.1.apk` from this repository's [Releases](https://github.com/erickmendes22/iss_android/releases). Version **0.1**, Android version code **8**, package `com.daedalus.iss.bionic`.

- Android 11 or newer, ARM64 and a compatible Vulkan GPU/driver.
- Snapdragon/Adreno is the primary target; other GPU families have not been validated.
- Supply your own compatible inFAMOUS Second Son game files. No game, firmware, keys or Lossless.dll are included.

The Bionic APK updates the previous Bionic builds using the same package and signing certificate. Keep app data to retain settings and saves. The older glibc APK is a separate application.

## Getting started

1. Install the APK and grant the requested file access.
2. Select the extracted game folder, or install a local game PKG directly into an empty folder. A prepared game folder contains `eboot.bin`, `art/` and `sce_sys/param.sfo`.
3. If needed, install the matching update PKG into the selected game folder.
4. Choose video, FPS and control settings, then tap **PLAY**.

English is the default language. Select Portuguese in **Settings → Language**.

## Features

- Optional image filling only in Home → Video (off by default); 16:9 uses an immersive display, and Android game classification is enabled.
- Native PCM audio mixer feeding Android AudioTrack at fixed s16 stereo / 48 kHz.
- inFAMOUS-themed launcher and in-game menu, opened from the left edge or Back/Guide.
- 30/60/120/240 FPS targets, VSync toggle and display refresh-rate requests.
- Bundled Turnip Gen8 V38 Patched for Adreno 8xx and Turnip v27.0.0-R1 for Adreno 6xx/7xx; custom driver ZIP import.
- Bloodframe frame generation, configured separately from the game's source FPS. Import your own `Lossless.dll` to use it.
- Configurable HUD, touch controls and physical controllers; incremental D-pad swipes with held-direction continuation and a two-finger TOUCH action.
- Graffiti controls: L3+R3 toggles the virtual sensor, ✕ shakes the can, R2 sprays and the left stick aims.
- Direct PKG installation, save import/export and log export.

FPS selections are targets; achieved performance depends on the scene and device. After a reported lighting regression, this build restores the GPU paths before the recent PC performance and subgroup/ballot ports, while retaining the safe cache-saver startup order. Device confirmation is pending. Shader caches refresh automatically after the update.

## Documentation and source

- [Release 0.1 notes](docs/RELEASE_NOTES_v0.1.md)
- [Controls, PKG installation, saves and Bloodframe](docs/ANDROID_BIONIC_PLAYER.md)
- [Release build and publication](docs/RELEASE_BUILD.md)
- [PC performance ports](docs/BIONIC_PC_PERFORMANCE_PORT.md)
- [Original PC documentation](README.upstream.md)

The release source is on `experimental-bionic`; the name is retained for branch continuity. The historical Linux/glibc integration remains separate. Native builds use `.github/workflows/bionic-experimental.yml` and `tools/android/bionic/`; the workflow prepares signed assets without publishing a release automatically.

## Credits and licenses

[HayatoG/iss_pc](https://github.com/HayatoG/iss_pc), [shadPS4](https://github.com/shadps4-emu/shadPS4), [FEX-Emu](https://github.com/FEX-Emu/FEX), [ducvd89/Bloodborne-Android](https://github.com/ducvd89/Bloodborne-Android), [Daedalus GoW3](https://github.com/erickmendes22/gow3_android_daedalus_fork), [fexdroid](https://github.com/cobrabm12/fexdroid), Bloodframe, Mesa/Turnip, StevenMXZ and all upstream contributors.

Source licenses and notices are preserved in the repository and packaged runtime. See [port/LICENSE](port/LICENSE) and [runtime notices](tools/android/bionic/NOTICE.txt). No affiliation with Sony Interactive Entertainment or Sucker Punch Productions.
