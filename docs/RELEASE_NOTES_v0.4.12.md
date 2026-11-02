# Daedalus Android v0.4.12 - earlier texture reclamation

The v0.4.11 S25 Ultra test still ended in `LOW_MEMORY` despite texture
collection activating at the previous 1.5 GiB RAM/swap headroom trigger.
Android now begins pressure-aware texture reclamation when available RAM or
free swap falls below 3 GiB, giving unused images more time to be evicted
before memory gets critically low. Set `TLG_GC_ANDROID_FREE_MB=0` to disable
this behavior.

This may increase texture re-uploads and cause stutter. It is a targeted
stability experiment, not yet a confirmed fix; sustained on-device validation
is still required.
