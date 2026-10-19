# Preserve valid pages of a mapped readback

## Device result motivating the change

The attached `iss-android.log (7).txt` runs abac2ad on CUSA00004 01.00,
1280x720, Relaxed, DMA off, Adreno 830 and the existing Turnip Gen8 driver.
Its three initial non-gameplay windows are excluded from the gameplay comparison.
Using sum(frames)/sum(window_s), gameplay windows 3–7 (BionicPerf indices 5–9)
are 41.443957 FPS, with 15.765245 recorder CPU ms/frame and 65.017950 GPU wait
calls/s. The two d988cb8 sessions previously measured 41.181165 and 40.199601 FPS
in those windows. One abac2ad session does not establish a stable improvement.
First seven gameplay windows, including save loading, are 39.925905 FPS.

The native overwrite proof still has little coverage: 67 applications versus
8684 fallbacks across the complete native-counter reports (0.766%). Of those
fallbacks, 6921 fail byte coverage and zero fail backing validation. The listed
slow writes at 0x5421fb still total 4886 calls and 46894.1 ms across overlapping
host threads. This is not elapsed GPU time and must not be converted to FPS.
The first seven readback reports contain 2252 requested retries, 2124 successful
retries and 71 final synchronous downloads. These reports have independent
boundaries from BionicPerf; no exact per-frame ratio is inferred from them.

## Concrete inefficiency in abac2ad

The mapped readback may collect dirty islands from multiple 4-KiB pages in a
64-KiB or 512-KiB envelope. Both MarkDeferredStale (before shader/protection
changes) and NoteDeviceWrite (the recorded write hook, including uploads and
copies) invalidate the entire snapshot when one copied page changes. The other
copied pages can still be valid at the original submitted tick. Previously they
were recovered and requested again too. The logs establish frequent retries;
they do not identify how many affect only a neighboring page. That fraction and
the resulting device FPS remain to be measured.

## New policy

Only native Bionic Relaxed mapped reads use page-scoped GPU invalidation. DMA,
Precise, staging readbacks and non-Bionic paths retain whole-snapshot recovery.
CPU writes without a fault still invalidate the full snapshot and synchronize
against a publication in progress. No CPU/GPU byte is inferred from value equality.
`TLG_MAPPED_READBACK_PARTIAL=0` restores the earlier mapped policy.

Each mapped snapshot records the pages invalidated by GPU writes. After its
original GPU tick has completed, capture revalidates both that page set and the
write-tick tracker. All-valid copies take the normal one-check-per-copy path.
Changed copies are split at page boundaries. Capture neither dereferences nor
invalidates mapped spans on rejected pages. For non-coherent memory, invalidation
is clipped to valid spans, rounded only to an atom of at most 4 KiB, inside the
existing page-aligned sparse residency allocation. The original HOST_READ barrier
and semaphore completion requirement are unchanged.

Finish publishes valid copies using the existing per-region locked GPU-bit
checks and copy-page grouping. It returns only rejected old copy ranges to the
GPU dirty set; any newer GPU ranges already there remain present. It marks the
snapshot applied with a release store after restoring the rejected ranges. A
waiting request checks its own page after acquiring that flag: an excluded page
uses the existing bounded asynchronous retry, whereas a valid page completes.
Other threads share the same once-only publication, including when their requested
pages have different validity. A CPU write to a valid published page cannot be
replayed over by the rejected neighbor's retry.

When another cache operation must settle a partially invalid snapshot whose tick
is still incomplete, it conservatively recovers it without waiting on the owner.
This retains the previous no-wait stale recovery instead of introducing a new
GPU command-thread stall. An all-invalid snapshot is likewise fully recovered.
Unknown/failed mappings and failed cache invalidations retain the old fallback.

A related abac2ad corner case is corrected too: if a shared completion has already
cleared the requested native store's GPU bit by the time its owner callback runs,
the callback skips starting an unrelated neighboring prefetch. No ARM store is
emulated in that case; OnWriteFault performs its normal locked CPU transition and
the original instruction executes, or handles any newer GPU write through the
existing path.

## Verification and device check

The mapped fixture compiles the production DeferredReadback type and methods with
pinned Vulkan-Hpp and UBSan. It exercises pre-write invalidation, device-write
notification, independent tick revalidation, whole-snapshot CPU invalidation,
all-invalid recovery, no-wait in-flight recovery, opt-out and normal all-valid
publication. Rejected spans have deliberately unreadable pointers in two-page
cases. A continuous three-page copy with its middle page invalid checks the
physical non-coherent invalidation ranges, both valid side pages, protection of
the middle page, exact dirty-byte restoration and preservation of a subsequent
CPU write while fetching the newer middle page. Newer GPU bytes outside the old
copy are retained as well. The serialized queue fixture executes production
ReadMemory for valid and excluded requests after partial completion, including
asynchronous progress and acquisition of the newer GPU generation.

The added `mapped partial` counter reports snapshots partially published, valid
copy bytes retained and invalid old copy bytes restored to the dirty set. Restored
bytes are not a count of actual subsequent transfers: newer writes may add bytes
and subsequent overwrites may eliminate them. Zero partial publications means
this optimization did not find an applicable snapshot in that test. Compare FPS,
retry counts, slow-fault sites and image correctness in the same scene; host tests
cannot prove a device FPS gain or absence of visual regressions.
