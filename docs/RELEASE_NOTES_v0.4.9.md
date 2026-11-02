# Daedalus Android v0.4.9 - memory diagnostics

This is a diagnostic build, not a confirmed out-of-memory fix.

The Galaxy S25 Ultra still receives an Android `LOW_MEMORY` kill on v0.4.8,
including with frame generation explicitly applied off. Disabling frame
generation alone therefore does not resolve the observed startup failure.

Under memory pressure, the native log now reports why buffer residency cannot
be reclaimed: shared heaps, partial allocations, recently used allocations,
in-flight submissions, or GPU-modified data. The report includes the current
submission tick and idle grace period, even during startup before that grace
period expires. Reports are limited to one every five seconds.

Texture-cache reports also count GPU-written tiled images retained in the
reported pass. These images cannot currently be safely written back by the
collector. The count describes that pass, not the entire cache.

The existing eviction safety rules and 256 MiB buffer release limit remain
unchanged. Shared heaps and GPU-only data are not discarded to avoid a kill.
Export the latest log from the launcher's Settings after a failed run.
