# Exact copy destinations for mapped-readback invalidation

## Device evidence (24c4b2d, log 8)

CUSA00004, 720p Relaxed, DMA disabled. Weighted stable gameplay windows
(BionicPerf indices 5 through 9) measure 42.395210 FPS versus 41.443957 in
log 7, a 2.30% session-to-session increase. Recorder CPU time rises from
15.765245 to 16.821714 ms/frame, while aggregate GPU-wait host time falls
from 11.779750 to 10.508776 ms/frame. These are overlapping host measurements,
not GPU execution timestamps; one session does not establish causation.

Page-scoped readback is active: the complete reports contain 99 partial
snapshots retaining 90,684 bytes and restoring 384,316 invalid old bytes.
In the first seven readback reports, final synchronous downloads fall from
71 to 11, but retries remain high (2378 requested, 2338 applied). Their report
boundaries differ from BionicPerf. The remaining bottleneck is not explained
by counting synchronous downloads alone.

## Source defect and correction

Runtime::CopyBuffer uses a single bounding range when a command has more than
four regions. That is conservative and useful for Vulkan barrier tracking.
However, AccessBuffer also feeds that bounding destination to NoteDeviceWrite.
An upload to scattered CPU-modified islands therefore advances the write tick
of untouched pages between them. It can invalidate a pending mapped snapshot
on an untouched page, or force its next readback to wait for the newer tick.

The fix retains the exact original Vulkan command and both bounding barrier
ranges. Only the write hook receives exact destination ranges. Consecutive
regions that overlap or touch are combined without sorting or allocation;
contiguous large uploads still require one notification. Non-sparse destinations
and absent hooks skip that extra traversal. Normal shader, clear, tiling and
small-copy AccessBuffer calls retain their notifications, even on barrier memo
hits. Actual writes still request HOST_READ visibility before submission.

This addresses CPU uploads (SynchronizeMemory/UploadInParts) in particular.
HLE copy shaders can separately mark their whole writable binding through
ObtainBuffer(..., true), before recording copies. This change does not remove
that separate conservative invalidation or claim to solve it. Narrowing arbitrary
shader bindings without proving their actual write footprint would be unsafe.

## Validation and limits

The host fixture compiles production CopyBuffer, AccessBuffer, AccessBufferImpl,
NoteBufferWrite, BufferCache::NoteDeviceWrite and DeferredReadback with pinned
Vulkan-Hpp and UBSan. It intercepts the Vulkan copy command and checks every
recorded offset, size and ordering; bounding conflict queries and barrier writes
remain present. Cases include 1/4/5/9 regions, unsorted destinations, cross-page
writes, untouched pending snapshots, Relaxed/Precise, successive submission ticks,
contiguous batches in both orders, device-local destinations, no hook and barrier
flushes. The previous CopyBuffer fails the untouched-gap tick assertion under
the same harness. The corrected version preserves the gap snapshot and still
invalidates both pages touched by an unaligned write.

Existing lifecycle tests separately exercise publication, invalid span rejection,
non-coherent cache invalidation, CPU writes and asynchronous retries. FPS impact
and how often scattered uploads hit pending snapshots require a new device run;
the existing logs do not record individual copy-region layouts. No measured
performance gain is attributed to this source fix before that run.
