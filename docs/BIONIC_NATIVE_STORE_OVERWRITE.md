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

## Device result and combined fallback (2026-10-19)

Both new logs test d988cb8, 720p Relaxed, DMA off, the same Adreno 830 and
Turnip driver. The counters do not support a performance win from the overwrite
proof. Across the complete reports, log (5) applied 55 of 8496 queued candidates
(0.647%); log (6)(1) applied 76 of 9853 (0.771%). Coverage rejected 7314 and
8636 respectively; backing validation rejected zero. These denominators include
loading and different recording durations and must not be used as FPS windows.

Using the same window selection as the earlier e9ba502 comparison:

| Build / session | First 7 gameplay windows (includes loading) | Gameplay windows 3–7 | Recorder ms/frame, windows 3–7 |
| --- | ---: | ---: | ---: |
| e9ba502 earlier baseline | 39.122 FPS | 40.927 FPS | 16.330 |
| d988cb8 log (5) | 39.709 FPS | 41.181 FPS | 16.050 |
| d988cb8 log (6)(1) | 38.604 FPS | 40.200 FPS | 16.760 |

FPS is sum(frames)/sum(window_s), not an unweighted average of rounded FPS.
There are three initial non-gameplay windows; gameplay 3–7 therefore selects
BionicPerf indices 5 through 9 inclusive. The between-session spread crosses
the older baseline. It does not establish either a gain or a regression.
The dominant original write site remains 0x5421fb. HLE bulk merge remains unused.

The failed proof nevertheless added a synchronous owner-queue round trip before
OnWriteFault queued its actual deferred readback: typically about 1000 rejected
commands every five seconds, each with a wake/wait handshake. This is a concrete
cost of the previous patch, even when the GPU readback itself cannot be avoided.

ReadMemory now accepts an optional owner preparation callback. CpuStoreOverwrite
resolves pending images on the caller, then runs its original conservative proof
inside the initial readback command. On rejection the **same command** records or
joins the ordinary deferred readback. The guest still waits on the GPU semaphore
outside the owner and retains the bounded stale retry/publication path. The
preparation is run only once, including when retries end in synchronous fallback.
On proof success no readback is recorded and only the existing successful-store
path advances the suspended ARM PC. On rejection, normal OnWriteFault still runs;
its locked GPU-bit check normally finds the data already published, or handles a
newer GPU write through the original path if it raced with completion. Texture
invalidation and write-fault extension remain in that ordinary path.

The proof, partial-page exclusion, byte values, GPU tick, wait ordering and DMA
policy are unchanged. This removes the extra queue operation; it does **not**
remove the waits preserving other GPU bytes in the page. No device FPS gain from
this correction has been measured yet. Host fixtures check the actual ReadMemory
method: accepted preparation skips recording, rejected preparation shares one
initial queue command, the owner progresses while the guest waits, successful
retry publishes the newer generation, and exhausted retries do not rerun the
proof. Ordinary read/write, joined-fault and newer-GPU-write race tests still run.
