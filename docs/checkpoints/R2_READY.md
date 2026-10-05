# R2 reviewed source change awaiting guarded application

The exact source transformation and before/after SHA-256+Git blob hashes are saved in this commit. This checkpoint is not a claim that the production files have already changed.

A one-shot Actions workflow applies ONLY these 11 reviewed files, verifies their bytes, runs regression and real JDK17 Forge compilation, and then makes a normal non-force source commit on chat/sr-0402 only if the branch has not moved. It refuses concurrent changes and does not touch main. No credentials or external patch payloads are read by the transform.

Local production core: 144 asymmetric cut cases / 17520 checks PASS. Local production planner with explicit NBT/world adapters: 16 cases / 208 checks PASS. Previous core/model regressions rerun PASS. Actual Minecraft/GPU/world placement still not tested. R3 temporary closure and R4 warm rendering cache are NOT included.

R2 behavior: each direction's outermost lane can detach; raw lane identifiers and world median/opposing lanes remain fixed while physical outside edge narrows. Median paint/rails/greenery/lights and arrows use the shifted local median coordinate. Wrong-sign/inner-lane cuts and whole-road-empty results are rejected. Single lane per direction may leave if the opposite lane remains. Cross-segment/cross-width-transition arbitrary cuts are not implemented.
