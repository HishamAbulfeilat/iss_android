# inFAMOUS Second Son — Daedalus Android v0.2.5 (Galaxy S25 Ultra, fork build)

## FPS experiments you can switch on and off

- **Renderer corrections is now in the launcher** (Display and performance). The dialog existed
  but no menu opened it. Its options apply on the next game start:
  - *Real light occlusion*: real GPU occlusion queries for lights. Turn off to test FPS; lights
    may show through walls.
  - *Dynamic compute stride (smoke)*, *Batched texture write-backs*, *Smaller memory blocks*.
  - **New, off by default — Experimental: hot pages.** In the S25 Ultra logs the main game thread
    spent ~25% of its time in write faults on pages it rewrites every frame. Hot pages keeps
    such pages writable and uploads them on use. Upstream measured it slower in one PC scene,
    so compare FPS with it on and off.
- Readback: use **Relaxed 1**. The app itself marks Precise 2 as a major FPS cost.

## Previous: v0.2.4

## Back to the original renderer behaviour

v0.2.3 logs showed lower FPS than the original build. Three of the fork's own changes cost
frames without freeing memory, so they are removed:

- **No forced texture budget:** the 45%-of-RAM cap evicted nothing (GPU memory stayed at ~5.9 GB,
  `0 images evicted`) and only kept the collector in pressure mode. The driver's live budget is
  used again, as upstream does.
- **No automatic frame-limit halving when the phone is hot:** at SEVERE it dropped 60 to 30 FPS
  and switched the panel between 60 and 120 Hz. Thermal status is still logged.
- **Buffer residency back to 16 MiB granules / 256 MiB chunks:** 4/64 did not lower total GPU use.

Kept: the frame-generation cap (real FPS ≤ refresh / multiplier), the exit-reason and memory logging.

## Restore the settings the reinstall reset

Uninstalling for the fork build wiped the app's settings and shader cache. The original setup
in the logs used **Readbacks: Precise 2 (Hybrid)**; set it again under **Readback — smoke and powers**.
Bloodframe **x2** (not x4) with a **60 FPS** limit. The first sessions rebuild shaders and stutter.

### v0.2.3

## Higher in-game FPS: less GPU memory, less swapping

The v0.2.2 log showed why gameplay fell to 11–30 FPS: with GPU memory pinned at ~5.7 GB,
Android swapped the game's own memory out (game RAM fell from 2.5 GB to 0.7 GB, ~0.9 GB in
swap, only 0.6–0.8 GB free). Every touch of a swapped page stalled the game for 20–60 ms.

- **Buffer memory is committed in 4 MiB granules from 64 MiB chunks** on Android (was 16 MiB
  from 256 MiB). Scattered game buffers no longer round up to 16 MiB of real RAM each, so less
  is held and less of the game gets swapped. The log line `GPU: buffer residency in 4 MiB
  granules from 64 MiB chunks` confirms it. Both sizes can be overridden with
  `TLG_RESIDENCY_GRANULE_MB` / `TLG_RESIDENCY_CHUNK_MB`.
- The previous session ended with `USER_REQUESTED` (the app was swiped away), not a crash.

## Settings worth testing on the S25 Ultra

- **RAM Plus off** (Settings > Device care > Memory > RAM Plus, then reboot). RAM Plus swaps to
  storage, which is what makes the stalls so long. Turning it off makes Android close background
  apps instead. If the game then starts closing mid-game, turn it back on.
- Bloodframe x2 with the 60 FPS limit (120 Hz output), FSR3 Performance, output 1280x720.

### v0.2.2

Diagnostics release for the mid-game closes. In the v0.2.1 logs the app still ended with no
error line about 30 s after free RAM fell to ~1.1 GB, even with the 5 GB texture budget:
GPU usage stayed at 4.8 to 5.8 GB because most of it is buffer memory the collector cannot evict.

- **Why the app last closed:** on launch, `iss.log` now starts with Android's own record of the
  previous exit (`Previous exit: LOW_MEMORY ...`, `CRASH_NATIVE`, `SIGNALED`, ...) with its
  memory use at that moment. Please play until it closes, reopen the app, and send that log.
- **Game memory in the log:** the memory line now appears every 10 s and includes the
  native game processes' resident and swapped memory.

Tips until the memory cause is fixed: close other apps before playing, and keep Samsung
RAM Plus on (Settings > Device care > Memory > RAM Plus). The first sessions after a
reinstall also freeze for up to ~25 s while shaders are rebuilt; that stops once the cache fills.

### v0.2.1

Built from upstream `experimental-bionic` plus fixes found in on-device logs from a
Galaxy S25 Ultra (Snapdragon 8 Elite, Adreno 830, 12 GB RAM).

### Fixes

- **Mid-game app closes (low-memory kill):** the logs ended without any native error, freeze
  report or "game exited" line, so Android closed the whole app. Vulkan usage sat at ~6.1 GB,
  above the driver's shrinking budget, while the guest reserves ~5.3 GB. The GPU texture collector now
  uses a budget of 45% of the phone's RAM (about 5 GB on a 12 GB phone), never more than the
  driver's live budget, so images are evicted before Android kills the app.
- **Frame generation no longer renders frames that cannot be shown:** with Bloodframe x2 and a
  120 FPS limit the game rendered up to 120 real frames, so the output was 240 FPS on a 120 Hz
  panel and half the generated frames were dropped. The real-frame limit is now capped at
  refresh / multiplier (60 real frames x2 = 120 Hz on the S25 Ultra). Your saved choice is kept;
  the launcher log shows `source limit=60 (chosen 120)`.
- **Sustained play and throttling:** frame rate fell from ~55 to ~30 FPS within minutes while
  the work per frame stayed the same, which points to thermal throttling. The app asks Android
  for sustained performance mode where it is offered, logs thermal status changes, and lowers
  the real-frame limit one step at SEVERE until the device cools back to MODERATE.
- **Better crash evidence:** `iss.log` now records RAM availability every 30 s and every Android
  memory-trim event.

### Recommended settings for the S25 Ultra

- Output 1280x720 with the 720p render patch, or FSR3 720p Quality, instead of native 1080p.
  The 1080p log spent 3.2 to 3.7 s of every 5 s blocked in `vkQueueSubmit`, so the GPU was saturated.
- FPS limit 60, Bloodframe x2 (120 Hz output). Or limit 30 with x2 for long sessions.
- The pinned Gen8 driver (the custom driver had no pipeline cache yet, so it recompiled shaders).

### Installing

This APK is signed with the fork's own key and cannot update a build signed by upstream.
Export your saves from the app (save ZIP export), uninstall the old app once, install this APK,
and import the saves. Later builds from this fork update in place.
