# Native FEX store overwrite: inFAMOUS Bionic

## Device evidence

`iss-android.log (4)(1).txt`, build cf30f21, confirms Relaxed, DMA off,
Adreno 830 / Turnip Gen8 V37 Patched and 720p. Every reported slow origin is
classified as a write. The top-site reports include 4792 slow faults at guest
PC `0x5421fb`, totaling 48604.8 ms of overlapping host-thread time: about 64.2%
of the time of the **listed sites**, not all process time or GPU frame time.
LLVM 20 decodes its ARM word `3d800299` as `STR Q25, [X20]`.
Related sites `0x54223a`, `0x54226e`, `0x542280` emit stores of Q16/Q21/Q17 at
X20+16/+32/+48. Together with another STR Q site, this class represents about
74.2% of listed time. This is evidence of actual FEX stores, unlike the previous
HLE write-merge experiment, which recorded zero applications.

## Conservative fast path

The native CPU bridge recognizes only aligned, unsigned-immediate STR Q stores
inside the suspended thread's FEX code buffer. It copies the exact 16-byte value
from the kernel's FP/SIMD signal record. No x86 instruction, floating-point
calculation, register, flags, writeback or following TSO barrier is guessed.
Loads, scalar/vector-lane stores, pairs, unaligned addresses, atomics, foreign
host code, missing/malformed FP records and invalid guest addresses fall back.

The GPU owner accepts the store only if **all GPU-dirty bytes in its 4-KiB page
fit inside the 16 bytes being replaced**. The check includes outstanding deferred
snapshots, whose ranges have temporarily left gpu_modified_ranges; stale but
unrecovered snapshots are included too. Eager state is conservatively excluded.

For an accepted store, the owner finishes earlier host copies reading that page
and queued host signals, settles pending image publications, invalidates/recovers
older buffer snapshots, and writes the CPU value through the validated native
backing helper. That helper rejects holes, permissions and physical aliases
without partial writes. The overwritten GPU ranges can then be discarded and
the page can become CPU-modified/writable. Its next ordinary page upload is safe:
there are no untouched GPU-dirty bytes on it. CPU overwrite intent is preserved
even when the new bits equal the old CPU backing bits; no value-difference twin
heuristic is involved.

Only after success does the CPU bridge advance the saved ARM PC by four bytes.
All other signal-context bytes remain unchanged. A rejected store resumes through
the original write-fault/readback path. Already emulated stores are excluded from
the slow-fault origin profiler, which would otherwise attribute them to the next
instruction after the PC change.

This restriction matters for performance too. Applying a 16-byte merge while
keeping a large GPU-dirty page write-protected can turn one fault per page into
hundreds of faults/owner commands as the CPU fills it. Partial-overwrite pages
therefore retain the original path; this implementation does not create that
per-store fault storm. Its applicability to the measured scene is not guaranteed
solely by seeing STR Q: the dirty-byte coverage test must also succeed.

## Verification and comparison

`test_bionic_native_store.py` compiles the production decoder, FEX resume/FP-record
parser, page-overwrite method and fault dispatch with UBSan and a separate owner
thread. It checks the observed opcodes, all base/vector registers, offset bounds,
foreign code, FP-frame failures, unchanged context on fallback, exact PC advance,
neighbor dirty bytes, hidden/stale pending snapshots, CPU ownership, aliases via
the existing native backing tests, DMA/Precise/non-Bionic guards and opt-outs.
Host tests establish policy/context correctness; they do not establish FPS gain.

The log adds `native store overwrite: N applied, M normal-readback fallbacks (coverage C, backing B)`.
Fallback counts cover queued candidates rejected on the owner; earlier policy
exclusions are not counted. Coverage and backing counts distinguish an incomplete
overwrite proof from a rejected native backing store. Compare applications, slow faults at `0x5421fb`,
GPU wait calls, FPS, and image correctness in the same scene. Pending images and
host uploads can still require waits, so success means no **buffer** readback for
this store, not removal of all synchronization.

`TLG_NATIVE_STORE_MERGE=0` disables the new path; `TLG_WRITE_MERGE=0` also disables
it. The original HLE memcpy/memset merge keeps its existing >=64-byte threshold.
No rendering quality, affinity, DMA or guest floating-point policy is changed.
