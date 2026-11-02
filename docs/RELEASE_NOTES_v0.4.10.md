# Daedalus Android v0.4.10 - staging pressure trimming

On Android, staging upload allocations no longer wait 300-600 frames to be
trimmed when available RAM or swap falls below 1500 MiB. Allocations unused
for at least one frame can be freed once their GPU timeline completes.
Deferred readbacks remain held until their owner releases them. The warm
ring floor and four-deletions-per-frame limit are unchanged.

Pressure reports include ring, dedicated, and held staging memory sizes to
measure this path independently of shared heaps and texture residency.

The v0.4.9 S25 Ultra test still ended in `LOW_MEMORY`. Its buffer reports
showed roughly 3 GiB of shared residency and no eligible idle buffers at
the reported checks. This change targets a separate, safely reclaimable
cache; it does not discard shared geometry heaps or GPU-only data.

This is not yet a confirmed fix for the on-device low-memory kill.
