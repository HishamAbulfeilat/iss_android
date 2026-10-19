# Reuse identical graphics descriptor state

## Device evidence from both new logs

Both `iss-android.log (10)(1).txt` (558 lines) and `(11)(1)` (586 lines) run
4667972 on CUSA00004 01.00, 720p Relaxed, DMA disabled, Adreno 830, coherent
mapped arenas and the same Turnip Gen8 V37 Patched driver. Both complete logs
were read. Neither records a crash at its end.

Weighted stable BionicPerf indices 5–9:

| Session | FPS | Recorder CPU ms/frame | Aggregate GPU-wait host ms/frame | GPU-wait calls/s |
| --- | ---: | ---: | ---: | ---: |
| Log 9, 36dce3c | 41.646683 | 16.374635 | 12.906152 | 43.325340 |
| Log 10, 4667972 | 41.926459 | 17.504376 | 8.774786 | 45.043965 |
| Log 11, same 4667972 | 43.500797 | 16.963905 | 11.450376 | 45.972887 |

First seven gameplay windows including loading measure 40.291262 and
41.220068 FPS in logs 10/11, versus 40.382420 in log 9. Warm-up differs
substantially (1.39 vs 0.48 seconds); these are not controlled scene/timing
replays and do not demonstrate a stable causal FPS gain.

The first seven readback reports show 491/44 narrowed prefetches and 317/523
coherent skips in logs 10/11. No wider pending wait was bypassed in either
session. Requested retries are 78/57; final synchronous downloads are zero.
Log 10 has no partial mapped publications; log 11 has 146 in those reports.
Report boundaries differ from BionicPerf. The much lower summed wait in log 10
with similar FPS reinforces that wait counters alone cannot predict FPS.
Host sums overlap between threads and are not GPU execution timestamps.

Recording remains expensive. Those stable windows contain 5,093,922 and
5,332,857 descriptor-template calls, plus 193,266 and 204,428 legacy calls.
Packed descriptor payload totals 723,924,576 and 766,662,048 bytes. This
establishes call volume, not how many descriptors repeat or that descriptor
processing alone is the frame's limiting stage.

## Concrete change

Pipeline::BindResources previously emitted push descriptors unconditionally,
including when the same graphics pipeline's complete descriptor payload was
identical to the preceding graphics binding. The new path compares the entire
packed payload and a unique template-lifetime key against recording-context state.
The comparison happens inside the emitted closure, in actual command order.
On an exact match it emits the invocation's push constants as before, but skips
the redundant descriptor driver command. Packing and recording-payload ownership
remain unchanged; no producer-side copying savings are claimed.
Resource resolution, image layouts, memory/barrier tracking, buffer-write hooks,
push constants and draw/dispatch commands still execute normally.

The cache is deliberately narrow: graphics set 0 only, identical template
lifetime only, at most 2048 payload bytes, no hashes or inferred layout
compatibility. A new 64-bit serial is assigned when a template is built, so
recycling a Vulkan handle or Pipeline address does not match an old lifetime.
The cache owns its payload independently of temporary producer/recording storage.
Compute descriptor bindings have a separate Vulkan bind point and still emit.

State is invalidated on a new command buffer and inside the actual closures for
graphics legacy pushes, descriptor-set binds, and the deferred scene-resolution
helper. Compute and push-constant-only commands do not disturb graphics set 0.
The owner never predicts a hit while preparing a chunk: owner hooks can insert
helper bindings, so comparing before Record would be unsafe. State comparison,
mutation and helper invalidation occur in recording order, including on the worker.

Every raw Scheduler::CommandBuffer access first synchronizes recording and then
disables reuse for the rest of that command buffer, including after a return to
threaded mode: an untracked caller may retain its handle. New command-buffer
allocation re-enables reuse after the previous stream has synchronized. No cache
state is changed from the owner while the worker executes commands. Direct mode
without raw access (including an oversized-chunk transition) still supports reuse.

Current graphics descriptor writers were audited: blit helpers obtain the raw
command buffer; the presenter's postprocessing likewise follows
draw_scheduler.CommandBuffer; the deferred scene-resolution helper invalidates
inside its own recorded closure. Compute-only helpers do not write the graphics
binding point. No unrelated helper or upscaler is enabled.

Only Android Bionic enables this policy; `TLG_DESCRIPTOR_REUSE=0` restores the
previous emission path. `BionicDescriptors` now reports `graphics_reused` and
`graphics_missed` plus `graphics_raw_bypassed`; existing `template_calls` and
`payload_bytes` count actual emissions. Payload bytes still copied into recording
chunks are not represented by the avoided-driver-payload count.
The old logs cannot establish a hit rate. If nothing repeats, this adds a bounded
snapshot/compare cost without removing driver calls, which must be assessed in
the next device result rather than presented as a measured FPS improvement.

## Verification

The production BuildDescriptorTemplate, BindResources, ReuseGraphicsDescriptors,
CommandBuffer, InvalidateVertexInput, InvalidateGraphicsDescriptors and new-command
buffer allocation methods are compiled with pinned Vulkan-Hpp and UBSan. Repeated
payload tests check actual intercepted descriptor commands and independently
changing push constants. Changed buffer offsets, image views/layouts, template
lifetimes, graphics fallbacks, raw access, helper invalidation and command-buffer
renewal must re-emit; compute updates do not suppress or disturb graphics state.
Both deferred and direct recording, deferred-to-direct transitions, cache opt-out,
producer mutation, recording-chunk lifetime and non-Bionic paths are exercised.
Fake Vulkan handles deliberately repeat across template lifetimes and command
buffers, so handle equality alone would fail these cases. A separate worker thread
executes the integration stream, and its owner checks reject owner-only operations
on that worker. Another case inserts a helper between producer preparation and
command execution, proving that matching occurs in the execution context. Raw
access disables reuse for multiple subsequent calls; command-buffer renewal
restores it. Pure snapshot checks
include invalid keys, empty/oversized payloads, every payload word and 10,000
reference comparisons. The pre-change BindResources fails the repeated-command
assertion under the same integration fixture.

This confirms equivalent command state with fewer duplicate descriptor driver emissions
in constructed workloads. It is not a measurement of the game's repetition rate,
Turnip driver savings, GPU time or FPS. Existing native readback tests remain active.

## Primary API contract

- https://docs.vulkan.org/refpages/latest/refpages/source/vkCmdPushDescriptorSet.html
- https://docs.vulkan.org/refpages/latest/refpages/source/vkCmdBindDescriptorSets.html
- https://docs.vulkan.org/spec/latest/chapters/descriptorsets.html

Push descriptor state persists until overwritten or disturbed; descriptor binding
points for different pipeline types are independent. This implementation requires
an exact lifetime key rather than trying to prove compatibility between layouts.
