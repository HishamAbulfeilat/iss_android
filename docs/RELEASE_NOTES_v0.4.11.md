# Daedalus Android v0.4.11 - lower staging reserve under pressure

The v0.4.10 S25 Ultra test still ended in `LOW_MEMORY`. Logs confirmed that
Android pressure trimming ran, but the staging ring held 128-256 MiB while
free swap fell below 500 MiB.

Android now prewarms two 16 MiB upload blocks instead of eight, and under
memory pressure each staging ring can trim down to one block (16 MiB), subject
to the existing four-deletions-per-frame limit and completed-GPU-tick checks.
The normal eight-block floor and prewarm count are unchanged outside Android.
Dedicated readbacks remain held until released.

This recovers only staging-buffer memory. It does not reduce the roughly
3 GiB shared guest geometry residency observed in the S25 Ultra log, and is
not yet a confirmed fix for the low-memory kill.
