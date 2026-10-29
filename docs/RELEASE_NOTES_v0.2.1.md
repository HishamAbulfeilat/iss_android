# inFAMOUS Second Son — Daedalus Android v0.2.1 (Galaxy S25 Ultra tuning, fork build)

Built from upstream `experimental-bionic` plus fixes found in on-device logs from a
Galaxy S25 Ultra (Snapdragon 8 Elite, Adreno 830, 12 GB RAM).

## Fixes

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

## Recommended settings for the S25 Ultra

- Output 1280x720 with the 720p render patch, or FSR3 720p Quality, instead of native 1080p.
  The 1080p log spent 3.2 to 3.7 s of every 5 s blocked in `vkQueueSubmit`, so the GPU was saturated.
- FPS limit 60, Bloodframe x2 (120 Hz output). Or limit 30 with x2 for long sessions.
- The pinned Gen8 driver (the custom driver had no pipeline cache yet, so it recompiled shaders).

## Installing

This APK is signed with the fork's own key and cannot update a build signed by upstream.
Export your saves from the app (save ZIP export), uninstall the old app once, install this APK,
and import the saves. Later builds from this fork update in place.
