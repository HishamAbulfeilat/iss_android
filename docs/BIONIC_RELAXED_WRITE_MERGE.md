# Native Bionic Relaxed CPU write merge

## Evidence and target

The e9ba502 device log (`iss-android.log (2)(1).txt`) uses Relaxed, DMA off,
Adreno 830 / Turnip Gen8 V37 Patched, 720p. Seven gameplay readback reports
contain 33 final synchronous downloads, against 597 in the first seven reports
of eddd39a. Yet the comparable, more stable performance windows 3–7 give only
40.93 vs 40.53 FPS. Wait calls increased from about 52.93/s to 63.75/s; these
are aggregate host waits, not GPU frame time. Reducing the cost of downloading
the same data again is no longer sufficient evidence of a useful FPS gain.

The HLE libc overrides in `port/src/runtime.c` route memcpy/memset >=64 bytes
through `tlggpu_cpu_write`. `TLG_WRITE_MERGE=1` was already configured, but
BufferCache::CpuWriteMerge explicitly required **Precise**, so this optimization
was inactive in all the recent Relaxed tests. A CPU store into a GPU-dirty
buffer therefore still entered the fault/readback path even when it was about
to replace known bytes. Inlined FEX stores and guest memmove do not use this hook.

## Implementation

Native Bionic Relaxed without DMA now accepts eligible 64-byte to 4-MiB writes:

1. Snapshot memcpy's source on the caller (or build the memset payload). Reading
   a protected source on the GPU owner could otherwise fault and wait for itself.
2. Queue one synchronous **in-order owner command**, rechecking buffer GPU dirtiness.
   This waits for the CPU recorder's order, not for the buffer's GPU completion.
3. Finish older overlapping host uploads and queued guest signals (CPU work),
   resolve pending image readbacks, invalidate older buffer snapshots, validate
   the complete destination and write through its backing view.
4. Register the exact written interval and invalidate overlapping cached textures
   before the owner can start another readback or upload.
5. Existing FlushMergedWrites uploads just those intervals before GPU use or
   download. It never marks the entire page CPU dirty: untouched GPU bytes survive.

Simply removing the Precise check was unsafe: the legacy writer stored backing
bytes on the guest thread and only subsequently registered the interval, leaving
an owner publication/upload gap. Relaxed's write and registration now belong to
one owner operation. Precise retains its existing policy and non-Bionic Relaxed
retains the ordinary fault path.

The native backing helper preflights every destination VMA, including permissions,
holes, reservations and physical aliases, before touching any byte. Validation
and copy hold the same mapping-table lock. Destinations with a second virtual
mapping of the written physical bytes are rejected. Overlapping memcpy ranges,
overflow and out-of-bound sizes fall back normally. An owner-thread call or a
caller holding the VMA exclusive lock cannot enqueue this operation.

Pending image readbacks may still require a GPU wait: their untouched bytes must
be retained. This change avoids **buffer** readbacks on eligible HLE overwrites;
it is not a general removal of GPU synchronization. DMA is excluded because a
backing store may then alias memory being written directly by the GPU.

## Validation and device comparison

`test_bionic_relaxed_write_merge.py` extracts the production merge, interval
registration, exact upload, native VMA helper and entrypoint guard. It compiles
and runs them with UBSan and a separate owner thread. Scenarios cover source
snapshotting, pending-image publication, host-upload source preservation, concurrent writes, the store/registration
gap, partial uploads that split intervals, unchanged neighboring GPU bytes,
permission/alias/gap rejection without partial writes, overflow, opt-outs, DMA
and non-Bionic/Precise policy. These host tests do not measure device FPS.

The readback report now includes `CPU write merge: N applied, M MB, R rejected`.
`rejected` counts owner/backend rejection after an eligible command was queued;
ordinary RAM and early policy exclusions do not increment it. This establishes
whether the game's measured scene actually uses the path. Set
`TLG_RELAXED_WRITE_MERGE=0` to disable this extension, or `TLG_WRITE_MERGE=0` to
disable all write merge. Compare the same save, camera, resolution, driver and
warm-up; device FPS and visual checks remain necessary before claiming a gain.

## Device result: 7d21076

The next full device log, `iss-android.log (3)(1).txt`, confirms the new build,
Relaxed, write merge enabled, and the same Adreno/driver/output configuration.
**Every CPU write merge report has zero applied and zero rejected.** Rejection
counts only queued owner/backend failures, so this does not distinguish missing
HLE calls from early size/source/policy exclusions. It does establish that no
reported write took the optimized path during this test.

Excluding the first three boot/menu windows, its first seven gameplay windows
contain 1370 frames / 35.06 s = 39.0759 FPS. The previous e9ba502 log had
1372 / 35.07 s = 39.1218 FPS. Gameplay windows 3–7 give 40.5826 vs 40.9273 FPS.
There is no measured improvement attributable to this optimization. Readback
wait calls remain about 64.88/s in those latter windows. The first seven readback
reports contain 41 final synchronous downloads, versus 33 previously; their
boundaries are separate from the performance windows.

The remaining bottleneck cannot be assigned to bulk HLE writes from these
counters. Native FEX stores, reads requiring GPU results, memmove, and early HLE
exclusions remain distinct possibilities. In particular, the wait-site source
line alone does not classify the original instruction as a CPU read or write.

### Targeted next capture

The Bionic comparison wrapper now enables the existing fault-origin profiler,
limited to handled faults taking at least 0.5 ms. It reports the eight origins
with most aggregate host time in five-second windows, separating read/write
and FEX guest / native host-call / host, with guest PC, thread, accessed address,
and the faulting ARM word when readable. ARM instruction reads use a fallible
self `process_vm_readv`; an unavailable PC cannot cause a recursive fault. The
old direct dereference of the guest stack was removed. Errno is preserved.

This is a diagnostic build, **not another claimed FPS improvement**. It identifies
whether the next correction needs precise native-store handling or an actual
GPU-result read path. `TLG_FAULT_PROFILE=0` disables the profiler; it also defaults
off with `ISS_COMPARE_STATS=0`. Ordinary short protection faults do not enter the
map or reconstruct guest registers, and unknown origins are bounded. Its times
are overlapping host-thread wall times and must not be summed into GPU frame time.
