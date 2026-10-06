# SR15 reviewed integrated RC — final verification pending

The integration review additionally reproduced real main-road guide generation at DETACH/TEMPORARY source contacts. It now suppresses auxiliary merge guides only for the actual departing host, not genuine arrival/BRANCH/EXTRA guidance. Added 16 identity cases; the pre-fix RoadJunction fails the same source-guides test. Final full CI must cover this reviewed code, not the earlier 32c6e980 candidate. Version remains 0.40.16-alpha-rc1, protocol62; no new JAR has been delivered yet.

# SR15 integrated candidate saved — final CI pending

2026-10-06. User requested all remaining changes before unified game testing. Work only on SR-0.40.15; baseline e9d4876 / production c62e440. Current candidate 0.40.16-alpha-rc1, network protocol62.

Implemented: SR15-03 narrow shallow foundation skirts, SR15-07 original-versus-visible terrain support classification, SR15-08 unchanged authored divider axes through consecutive merge/detach, transaction-local original-merge removal/restoration and continuation propagation, Y and 3–6 arm independent directional-lane configuration. Earlier stage1/stage2 changes retained. Details and acceptance sequence: docs/checkpoints/SR15-FINAL-428.md.

Local core, real generator and record/index tests ran, but final full regression and actual Forge build are pending for this saved commit. Run bash tools/check_sr428.sh. Do not substitute older CI passes for this source. No Minecraft client/GPU/original-world or real ServerLevel transaction acceptance. All original14 items await unified game acceptance, not screenshot closure.

Source is committed before CI; a following documentation-only checkpoint will record actual outcome and artifact hashes. Other branches unchanged. P2 terrain/shaders/multithreading not expanded. Previous checkpoint remains at e9d4876:PROGRESS.md and FINAL_VERIFICATION_427.md.
