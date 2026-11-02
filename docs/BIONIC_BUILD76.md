# Build 76 — v0.1, versionCode 15

Implements the first recommended PC audit group on the existing Bionic renderer,
not Renderer v2. Source reference: HayatoG/iss_pc `7a48167` and the original
approved Android backport `2c811fc`.

- Reintroduced R1–R8: sampler generation/LRU memo, bounded descriptor storage,
  GPU dirty-region summary, no-work image-download exit, normal-path shader
  cache loading, moved asynchronous cache writes, driver-cache save gating and
  bounded disk output, presentation clear/blit fast path including AHB/Bloodframe.
- Dynamic compute strides with shader cache salt, controlled by the Android
  launcher's `renderer_dynamic_stride` preference. Full source changes include
  descriptor eligibility and dynamic address calculation, not only an env flag.
- Real Vulkan occlusion queries with capability fallback to zero-count mode 3;
  preserve ordered draws/events and end active query segments at rendering cuts.
- Batched texture collector write-backs: retain images/page protection until
  copies finish; retain Android's early skip for GPU images that cannot be
  evicted; drain each staging batch at a 64 MiB target (one large image can exceed
  that target). GPU waits and FreeImage remain after required write-back.
- VMA preferred blocks 64 MiB, opt-out to allocator default; forced integrated
  memory budgets cannot exceed the driver's live nonzero budget.
- New renderer corrections dialog in launcher and player drawer provides four
  independent next-boot fallbacks for stride, occlusion, batched GC, VMA blocks.
- Required mobile swipepad replaces Touch. Mobile sensor button sets the native
  state explicitly, sharing state with the Android controller's L3+R3 chord;
  separate shake icon sends cross only in sensor mode outside menus.
- Action icons retain controller-command labels; player drawer can hide labels.
  Sensor button also keeps a lit outline when labels are hidden.
- Camera drag uses displacement per elapsed time instead of displacement per
  input event. Independent camera/spray sensitivities (25–300%) are saved on
  Apply. Virtual Android spray smoothing is reduced, speed increased; SDL real
  sensors and existing Classic fallback are preserved.
- Optional direct startup is shared by Home and player drawer, off by default.
  It only launches on fresh launcher creation with accessible game data; returning
  from the game or recreating the launcher does not auto-launch it again.

Not included: required-subgroup/ballot reintroduction, full Renderer v2/native
passes/AOT, huge-read truncation, stale/lagged readbacks, deferred graphics bind,
worker cache reseeding or experimental barrier merging. These require further
isolated validation; they are not represented as implemented here.

Validation: full Android Java compile; production overlay pointer/label/sensor
checks; specialization differential and allocation checks; production dirty-page,
descriptor/presentation tests; stride eligibility and query lifetime checks;
complete native Android CI build required before APK delivery. No FPS gain,
Adreno visual correctness or device crash-free claim follows from these checks.
