# Third-party attribution

This experimental Android adaptation is maintained by erickmendes22 / Daedalus Emulator.

- HayatoG/iss_pc at 1898d449a7edf506d12ef63445f8fc5432e60105: game runtime, renderer and game-specific corrections; original notices remain in port/gpu/VENDOR.txt and README.upstream.md.
- erickmendes22/gow3_android_daedalus_fork: guest_cpu/FEX bridge, portable ARM64 shader-resource-table walker, Linux ARM64 cross-build and rootfs tooling, Android controls and audio bridge, and Mesa patches. GPL-2.0-or-later where applicable; original copyright headers retained.
- ducvd89/Bloodborne-Android and deadinside28/bloodborne_pc: underlying port and Android infrastructure, credited through the adapted GoW III sources.
- FEX-Emu/FEX at fb925d6fc5cc8576bf0f9ae6978fc6d912485593: x86-64 to ARM64 execution; included as a pinned submodule with its own license files.
- cobrabm12/fexdroid at 2b03ec73c3507b640fba1942fb8d99b43b88945a: patched Linux rootfs and unmodified MIT-licensed display/input JNI bridges. Exact bridge hashes are recorded in app/bridge-provenance.json.
- Mesa/Turnip and the original authors of the retained driver patches: Vulkan driver builds; source revisions are pinned in tools/android/build/build_turnip.sh.

The build downloads a checksum-verified fexdroid rootfs and Debian packages; their package copyrights and notices are preserved. No game or firmware content is included. Further component attributions are retained in the upstream source tree.
