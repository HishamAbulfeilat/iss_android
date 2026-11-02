# Android language and render patch investigation — 2026-10-23

Base: `a879cb68f449aeaee262598f005821c267009787` (experimental-bionic).
Changes here are prepared locally; they are not in the previously delivered APK.
The approved Direto layout is integrated: aim, spray and power left; camera drag right.

## Game language

The old resolver only accepted `.xpp`, although installed language resources use
`.xpps`. With Brazilian resources present it could report no installed Portuguese
and overwrite both runtime language variables with English (PS4 language ID 1).
The resolver now accepts both extensions and selects Brazilian Portuguese (ID 17)
when the nonempty base text and audio resources exist. Patch-only `_p` archives
are not treated as proof that the base language is installed.

A bounded parser also recognizes language-specific chunks in
`sce_sys/playgo-chunk.dat`, following the layout documented by HayatoG/iss_pc.
This is used for archive layouts with no directly visible language packages;
common chunks and malformed metadata do not imply language availability. A
visibly incomplete text/audio pair still falls back to English. No game archive
is renamed or changed. UI language reaches `configure_language` through
`ISS_APP_LANGUAGE`; both `ISS_LANGUAGE` and the native runtime's `TLG_LANGUAGE`
are set before launch. One startup diagnostic reports the decision and resources.

Primary references:
- https://github.com/HayatoG/iss_pc/issues/184
- https://github.com/HayatoG/iss_pc/blob/main/port/launcher/infamous_run.py
- https://github.com/HayatoG/iss_pc/blob/main/docs/ROADMAP.md

## Low internal resolution patches

Integrated into the existing `port/patches/InfamousSecondSon.xml`, not a separate
catalog: 960x540, 854x480 and 640x360. Entries are disabled by default and marked
experimental. All three are selectable in Android's Patches panel. The game
output size is kept at 1920x1080; render dimensions change independently.

Binary used for validation: eboot SHA256
`2d1ca79630d7bbe6fa29575f59aa7996d43071d74ac8e4f7d40137967d9039ab`.
Normalised ELF addresses below are virtual offsets, not raw SELF file offsets.

Each new entry has ten writes:
- Constructor internal dimensions: immediate operands at `0x1eff4a`, `0x1eff51`.
- Runtime output dimensions: `0x1f0d39`, `0x1f0d48` (1920, 1080).
- Normal renderer internal dimensions: `0x1f0d3e`, `0x1f0d4d`.
- High-resolution renderer internal dimensions: `0x1f0d43`, `0x1f0d52`.
- Output selection CMOV instructions at `0x1f0d56`, `0x1f0d5c` are NOPed,
  matching the existing resolution patch's approach.

The extra high-resolution operands matter: only changing the existing normal
branch constants leaves the alternative branch at 3200x1800. Both branches were
executed from the reviewed straight-line resize block on an x86_64 host after
applying the new writes. For each patch both produced output 1920x1080 and the
requested internal width/height. Original code was also executed as a control:
1920x1080/1920x1080 and 3840x2160/3200x1800 respectively.

All ten catalog entries passed the production mask compiler against the real
linked executable: unique signatures, executable bounds and overlapping-write
checks. Each new resolution also compiled together with all three blur patches.
These checks do not establish Android GPU compatibility, visual correctness,
minimum resolution support or measured performance. 854x480 in particular still
needs device validation of the engine's surface/pitch handling.

## Optional shadow and light-beam patches

Two switches are now integrated into the same XML catalog and Android panel,
independent and disabled by default. Both additionally require the normalized ELF
SHA256 `b3afd173a379f83e1e408e71fdef22b78f93092d332b79decd48a18a08bfc605`.
An executable hash mismatch rejects the selected patch before any write.

### Shadow maps

A critical finding ruled out patching the shared draw function at `0x43d590`:
it also belongs to `CFxDepthOnly` (class ID 0x3c3), which uses wrapper `0x1a7b70`.
The patch instead jumps inside the shadow-only wrapper from `0x43d355` to its
original canary-checked epilogue at `0x43d438`. `CFxShadowMap` and
`CFxObjectReflectionShadow` use this wrapper; `CFxDepthOnly` does not.

Map preparation `0x439ab0` binds the shadow target, then calls depth clear
`0x301410` at `0x439b34`, before scheduling shadow effects (type 0x27) through
`0x198bc0`. Parallel dispatch also prepares the target before launching its jobs.
The original cached-map branch at `0x439b2c` skips clear for kind 2. This branch
is NOPed so cached maps also receive the existing clear. Clear uses the engine's
zero depth value and original state restoration. Resolve/conversion stays intact.

### Light beams

`CFxDeferredLightSpotBeam` has the dedicated wrapper `0x19f310`. The patch jumps
from `0x19f33f` to its original epilogue `0x19f450`, before uniforms and beam draws.
It changes only this beam wrapper; diffuse, ambient specular, other light types,
render-target setup and final composition are retained. It does not globally
turn off lighting or SSAO. Performance benefit depends on beam coverage.

### Binary validation and limits

All **192** allowed combinations (native or one of five resolutions, with five
independent disable switches) compiled against the real executable with unique
masks, valid executable bounds and no conflicting writes.

The actual shadow-preparation function was executed with external GPU calls
replaced by inert stubs and a counter at the clear entry point. Original/patched
clear counts were 1/1 for a null light, 1/1 for kind 1, and 0/1 for cached kind 2.
This proves clear control flow, not GPU output. The patched real shadow and beam
prologues/epilogues each returned normally 100 times with null job arguments,
showing that disabled paths exit before job-data dereferences. The shared depth
pre-pass and draw-function bytes were checked to remain untouched.

Android visual correctness, complete scene coverage, crash freedom and FPS
remain unverified. These are optional experiments with validated binary control
flow, not a promise of measured performance or guaranteed visual safety.
SSAO and shadow-map-size changes remain excluded: no reliable native neutral
path or proof of texel-size semantics was established for those candidates.

## Checks

- Game language suite: 5 passed, including real `.xpps` names, incomplete-pair
  fallback, both observed PlayGo mask byte orders, malformed tables and Android
  automatic locale resolution.
- Catalog suite: 5 passed, including conflicts and fail-closed mask handling.
- Binary mask and resize execution checks described above passed.
- On-device Portuguese, image correctness and performance remain untested.
