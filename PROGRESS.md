# SR-0.40.14 — P1 repair checkpoint (2026-10-06)

User baseline: `e87cec55171ed247db78c8e7951c5242713ed164`; ZIP SHA256 `b50662a8fc8a0e55848108525fc27b307f205942dccf8755270007c9b1aee8f5`.
Requested branch: `SR-0.40.14`; main and chat/sr-0402 are not modified.

Recovered existing P1 implementation from b0062c0, with provenance commit a6951038.
That candidate compiled with Forge but its full regression stopped at Closure418's obsolete two-component planter expectation. It was NOT an accepted release.

Current changes: integrated monotone signed endpoint-grade profiles; cap-aware feasible flat-port grade transitions; real obstacle-only corridor bounds (including already-clear zero-lift contacts); union overlapping closure planting intervals; preserve native five-component planting assertions, full depth/collision tests, and grade fixtures that distinguish real infeasibility from a discarded local hump heuristic.

Additional repair: automatic 64/96/128m early alignment before EXTRA intake, with the original B and fixed auxiliary tail preserved and all nonselected lanes still protected. Updated legal mirrored model fixtures; explicit illegal inner-slot EXTRA rejection and no-mutation cases remain. Source and acceptance matrix are in docs/checkpoints/SR-0.40.14-P1-review.md.

Verification is in progress. Local core/adapters are not Minecraft world/GPU tests. All P1-01..11 remain awaiting user in-game acceptance. P2 terrain/shaders/multithreading remain deferred. Do not mass-rebuild old saves or claim old fixtures are actual screenshots/world saves.

---
## Prior checkpoint (historical, not current validation)
# SR 0.40.15 实机反馈恢复检查点

本轮恢复开始UTC2026-10-06T10:42:07Z。已核验20be8837候选的真实Forge编译通过；当前基于该远端源码继续，不重做、不把0.40.14旧成功当作本轮结果。

补正：全路径坡度包络之后前向选取实际可达单调解；护栏共享边界按原始路面而非内缩栏杆面裁切，避免额外汇入隔栏；普通绿化床独立部件局部裁切，叶子碰匝道不能整米删除底座。新增30核心场景10509检查本地通过。

当前全量回归与真实Forge正在执行，结果未核验；普通汇流、307米8米高差和实际标线/预览边界继续核查。P1最新截图仍未实机结案；P2 terrain与多线程暂停。

保留已有FLOW枚举、全事务写入前预检、EXTRA邻道保护、孔区临空护栏以及共享普通道路绿化床。未运行Minecraft客户端、GPU或真实世界读写/保存重进。后续先核实本次CI，再从新回归继续，45分钟收尾50分钟检查点暂停。
