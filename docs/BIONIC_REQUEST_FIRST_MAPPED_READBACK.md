# Keep neighboring prefetches off the requested page's critical wait

## Device result: log 9, 36dce3c

Same CUSA00004 01.00, 720p Relaxed, DMA disabled, coherent host-visible sparse
arenas, Adreno 830 and Turnip Gen8 V37 Patched. All 454 log lines were read.
There is no recorded crash at the end of this session.

The first seven readback reports contain 65 requested retries and one retry
counted applied, versus 2378/2338 in log 8. There are zero final synchronous
downloads in these reports, versus 11 previously. That supports the previous
copy-footprint correction's intended effect, but does not establish its exact
per-event coverage: individual copy layouts are not logged.

FPS remains at the same level. Weighted stable BionicPerf windows 5–9 measure
41.646683 FPS versus 42.395210 previously (-1.77%). Recorder CPU time changes
from 16.821714 to 16.374635 ms/frame, submits from 6.996234 to 5.958733/frame,
and GPU-wait calls from 67.065868 to 43.325340/s. Aggregate GPU-wait host time
increases from 10.508776 to 12.906152 ms/frame. These overlapping host timings
are not elapsed GPU execution time. First seven gameplay windows, including
loading, measure 40.382420 FPS versus 40.119760 (+0.65%); shader cache contents
and loading compilations differ. Neither comparison establishes a stable gain.

Native overwrite still covers little (27 applications / 7064 fallbacks;
5103 coverage rejections, zero backing rejections). CPU HLE merge remains zero.
The slow native write at guest PC 0x5421fb is still prominent. This revision
therefore targets remaining wait selection, not those unsuccessful merge paths.

## Reproduced source problem

StartDeferredReadback collects a 64-KiB mapped prefetch and waits for the newest
write tick among all its copies. A requested page written at tick 7 can therefore
wait for a neighbor at tick 9. This happens even with tick 7 already complete.
Existing logic narrows the window when a neighbor is current/unknown/unmapped,
but not when that neighbor belongs to a newer submitted, unfinished tick.

A second caller can also join a wider pending mapped snapshot at tick 9 despite
only needing the page at tick 7. Merely rejecting that join is insufficient:
SettleDeferredReadbacks then waits for the same wide snapshot on the cache owner.

## Change

For active native mapped Relaxed reads with DMA disabled:

- A request whose GPU bit was cleared while queued returns immediately, without
  starting an unrelated neighboring prefetch.
- A 64-KiB prefetch with a newer unfinished tick than the requested pages falls
  back to the existing page-rounded required interval. Completed neighbors and
  neighbors sharing the same tick still batch normally.
- Joining cannot inherit a longer unfinished mapped wait from unrelated pages.
  Settlement recovers that wider mapped snapshot without waiting; its old dirty
  bytes are restored before recollection. Its existing guest waiters observe the
  recovered flag and use their normal retry path. Staging snapshots retain their
  previous settlement behavior and resource lifetime.
- A shared mapped snapshot already ready on the owner is published there, outside
  deferred_mutex, avoiding a second guest wait/publication command round trip.

The captured limit is only a selection hint. Prepare and capture still recheck
actual write ticks, mappings, mode and completion. A new upload/write after the
hint is captured cannot make new GPU data readable early. All dirty islands on
each requested page must still be collected before lifting page protection.
No CPU store is inferred, no byte-equality merge is used, and all HOST_READ
barriers, CPU-write invalidation and locked publication checks remain.

## Validation and device follow-up

The production-method Vulkan-Hpp/UBSan lifecycle fixture covers a requested page
at tick 7 and a neighboring page at tick 9: required work both unfinished and
already finished, neighbor already finished, both pages at the same tick, a
current-tick neighbor, a wider pending join, a ready shared join, and a queued
request already coherent. It checks the exact remaining dirty intervals and
that omitted pages stay protected and unpublished. The old guest's later retry
publishes its tick-9 data without replaying a new CPU byte on the completed page.
A non-coherent case poisons the omitted neighbor's mapped pointer and forbids
cache invalidation of its allocation, proving that only the requested data is read.

Existing current/unknown/mapping fallbacks, partial-page publication, concurrent
shared completions, newer GPU writes and CPU-write protection tests remain active.
The previous production methods fail the new requested-page priority scenario.

The new `mapped wait scope` line reports prefetches narrowed, wider pending waits
bypassed and coherent requests skipped. These counters establish policy coverage
in a future device log, not saved GPU milliseconds or FPS. Reduced prefetch can
also add later faults/owner requests, which is the main performance tradeoff;
ready and same-tick batching is retained to limit that cost. Actual performance
impact requires a new device run over the same gameplay segment.
