# PC performance ports, items 1–8 (Build 65)

Base: Bionic `0f9a9a4`; source: HayatoG/iss_pc, inspected through `fb7e452`.
Only the eight approved performance items are integrated. Android remains a native Bionic/FEX
build, with its descriptor templates, deferred host-copy synchronization, AHB presentation,
Bloodframe output pacing, language and driver selection preserved.

| Item | Upstream | Port |
|---|---|---|
| 1 | `9743950` | Memoized sampler handles with a generation check and locked LRU touches; eviction invalidates the memo. |
| 2 | `9743950` | Persistent descriptor-write storage, explicitly passing the active count through Android template, push and ordinary descriptor paths. Unused pointer fields are reset on reuse. |
| 3 | `7326e04` | GPU dirty-page summary skips empty-region scans. Set/clear accounting also runs with relaxed or disabled readbacks. |
| 4 | `af14cf5` (selected) | Skip image-download processing, upload-worker waits and locks when neither queued nor pending work exists. Keep periodic diagnostics and track every enqueue/clear/erase under the existing mutex. |
| 5 | `e3f7e99` (selected) | Try the saved shader program on a normal cache miss regardless of startup warmup mode. Worker lookups remain read-only. |
| 6 | `e3f7e99` (selected) | Move shader blobs and paths into the asynchronous write task instead of copying; retain atomic file replacement. |
| 7 | `8dec022` | Save the driver pipeline cache only after a successful new pipeline creation, including binary restore/compute paths; skip writing blobs over 1024 MiB by default. This is a disk-write limit, not a GPU-memory cap. |
| 8 | `028f31c` (selected) | Skip the clear only when the blit covers the destination. Clear letterbox bars, use nearest filtering only for a 1:1 blit, and retain linear scaling. Shared by regular and Bloodframe real-frame presentation. |

Fallbacks: `TLG_SET_WRITE_POOL=0`, `TLG_PRESENT_SKIP_CLEAR=0`, and the existing
`TLG_TOGGLE_FILE` sampler-memo bit (31). `TLG_DRIVER_CACHE_MAX_MB=0` disables the disk-write size limit.
No FPS gain or device gameplay result is assumed from build/tests alone.

## Validation

- Production descriptor methods run against Vulkan-Headers v1.4.363 with controlled driver calls.
  Extra inactive entries contain deliberately invalid pointers; templates, both push paths and
  ordinary updates must ignore them, including an empty draw.
- Production presentation helpers cover 1:1, up/downscaling, ultrawide and vertical letterboxing,
  plus the explicit fallback; tests count clears/barriers and inspect blit regions/filters.
- Production GPU region methods cover partial/final clears, region edges and all readback modes.
- Existing runtime suite and the native Android CI build remain required.

## Separate visual changes

The PC release `v2026.10.19-d3995fe` describes a **possible**, gameplay-unconfirmed fix for
flickering fog/water/fire and particle/light lists (PR #227). The existing Bionic occlusion mode
3 already suppresses excessive glare from fake occlusion results.

The new ballot alignment mask (`e51a229`, table item 27) is not proof of a visual correction:
`docs/TEST_PR227_MAIN.md` upstream notes that the SPIR-V backend already divides the shared
address by eight, making this extra mask produce the same index. The distinct subgroup/profile
consistency fix (`b37381d`, table item 26) is a further candidate, not part of the approved 1–8.
Neither is included here or represented as a tested graphics fix.

## Release 0.1 follow-up

Items 26 and 27 were subsequently approved and integrated: stage-valid required subgroup size,
matching compute translation profile, explicit aligned ballot loads, and shader-cache key inputs.
The driver-cache saver now starts after worker cache initialization. The ballot IR test and
controlled shared-memory pair tests check addresses and bounds; neither proves a gameplay fix.
The prior Build 65 exclusion above is historical. Public notes retain the visual-validation limit.
