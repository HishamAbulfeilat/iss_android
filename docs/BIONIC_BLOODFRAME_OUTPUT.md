# Bloodframe output pacing, language and Turnip update

The AHB swapchain forced FIFO in its constructor, `UpdateVsync`, and
`SetFrameGeneration` whenever Bloodframe was active. FIFO releases buffers at
SurfaceFlinger's latch cadence. On a 60 Hz display this backpressures the game
at approximately 30 real FPS for 2x and 15 for 4x, even with VSync disabled.
This is a compositor policy, not an eboot FPS patch or a limit in Bloodframe's
compute library.

All three paths now respect the saved VSync setting. The buffer count still
scales with the multiplier. With VSync disabled the existing newest-wins
presentation mode can replace excess frames without forcing the real-frame
producer to wait for every generated frame to be displayed. Acquire/release
fences and buffer ownership remain unchanged. VSync enabled intentionally
keeps FIFO and can still reduce the source rate to refresh / multiplier.

Comparison: GoW3 `experimental-bionic` f8a7748 and the later framegen branch
7ae099d allow `UpdateVsync()` to select non-FIFO regardless of multiplier.
The inFAMOUS copy had added a multiplier condition to that method too. This
change makes the policy consistent across initial setup and live changes.

The Build63 high-refresh window and AHardwareBuffer child-surface requests
remain in use. Generated/output counts are not proof of physical screen
refresh; Android, display mode and GPU throughput determine visible output.
Device verification is still required: VSync off, 120 or 240 source ceiling,
then Bloodframe off/2x/4x in the same scene. Record source/output HUD rates
and `Display: Android reports ... Hz` from the log. Repeat with VSync on to
verify the explicit FIFO option. No game binary patch is applied.

App language defaults to English independently of the phone's language;
Settings > Language offers English and Brazilian Portuguese. This changes
the launcher, setup, settings, in-game menu and controller guidance, not the
game's own assets/dialogue. Preference persists across app upgrades.

The bundled automatic driver uses KGSL chip ID 0x44... for Gen8 V38 Patched
and 0x43... (or legacy 0x06...) for v27.0.0-R1 on 6xx/7xx. Unknown GPUs use
the system Vulkan loader. Both ZIP and library hashes are pinned and checked
against the StevenMXZ v27.0.0-R1 release. Old implicit Gen8 selections migrate
to automatic once; explicit choices in the new UI persist.

Driver import accepts Turnip ZIPs with root meta.json and
libvulkan_freedreno.so. It bounds ZIP and extracted sizes, validates API level,
ELF64/AArch64/shared-library type and the exported Android HAL HMI symbol,
and installs only those two fixed filenames in a hash-addressed private
folder. A failed import does not change the selected driver. Imported drivers
survive bundled runtime updates; driver changes apply on the next game start.
