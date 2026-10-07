# SR432 performance verification in progress

0.40.19-alpha-perf2, protocol64. User requires both route planning and complete preview/build improvement. Exact raster/contact hot paths and repeated real two-scene tests preserved. No performance acceptance or user-world completion claim until measured gates finish. No fixed interactive timeouts restored.

---
# 当前：SR431 功能检查通过，整体性能验收不通过；不要发布perf1

仅工作分支SR-0.40.15。用户要求解决匝道预览/建造慢，而不是固定时间中止。候选0.40.19-alpha-perf1，协议仍64。生产修改e3ac9269ad13e89b11a8db207b4f23d2950d0d35，最终受测源码/JAR59988961fe5d2a2a21063672ff1978ad61cb07ec。之后仅文档提交。main、SR-0.40.14、chat/sr-0402没有修改。

## 当前结论

**没有完成用户要求的整体性能修复，不建议用户安装perf1重新测试。** 完整Java17 Forge、核心/模型回归及真实Minecraft两项预览预检->建造->删除都通过，但同一runner的单次旧/新配对中，路线约快20%，预检和建造反而变慢。

- ADD3->4：路线226.835739->179.089984ms；预检1960.555890->2300.711330ms；建造1494.340096->1684.775897ms。三阶段3681.731725->4164.577211ms。
- TEMPORARY/MERGE双向六车道高速：路线2330.008651->1851.187522ms；预检4478.029979->6331.447844ms；建造3600.090006->5635.180542ms。三阶段10408.128636->13817.815908ms。
- 退步主要集中在edit_raster。高速预检2489.646194->4052.176423ms，建造2384.270214->4366.328108ms。不能声称已证明新缓存是全部退步的唯一原因，需拆分剖析哈希/值比较、栅格化、合并、复制/分配与GC。

## 已保存改动

精确区间/扫描线替代相交区间对全路线扫描；几何、保护车道面与恢复扫掠复用；长路径空间候选筛选、短路径保留旧精确循环；平面碰撞盒快路径及有界全值相等碰撞栅格缓存。取消交互固定8/12/4/6秒截止，保留取消、有限队列、过期结果拒绝；没有关闭几何/世界保护。后台仅纯规划，最终世界写入仍主线程。

完整旧回归在37584513955的regression112671525201成功；2541647新等价检查、48长路径检查、12调度器检查通过。最终37585420394的package112674340232、paired_minecraft112674340317均SUCCESS；两版真实2项GameTest均执行和通过。CI绿灯不包含性能验收。首次配对0测试的命名空间错误被严格脚本拦住，修复后没有改换道路夹具或删除保护断言。

## 下一轮准确入口

先拆开RoadIndex.buildRaster缓存入口和RoadRaster.compactFlat快路径做消融，细分edit_raster耗时及内存分配，必要时撤回造成退步的改动；保留有正面证据的走廊优化。固定几何和选道参数，多次重复、交替顺序对照；预检、实际建造和删除的碰撞/拓扑结果必须一致。不要先加大缓存、提高时间上限或把收集用户日志当作修复已知退步的前置条件。

在完整流程没有改善前，不交付“性能已修复”的安装包。原用户存档、客户端GUI/GPU/车辆并未实测。详细数据、原始日志/产物校验、历史失败和边界见docs/checkpoints/FINAL_VERIFICATION_431.md。SR431-SAVED.md保存的是结果尚未结束时的检查点，以本文件和最终记录为准。

完整源码ZIP SHA256 1e43f6454b7d26342f9d70ab7bb1e2480bc8e42a2781cdeaf287efe17d21ab8c；不建议安装的实验JAR SHA256 077fb6fd3707b75655b6f9cd338e689bef13c1bc1915514362d497c5503ea07a。622个生产文件与完整回归提交逐字节一致。代码在活动工作结束前已保存，之后只收取已启动测试结果与归档；不承诺后台修改。

## 历史基线

上一交付0.40.18-alpha对应32cee0fae8a03726bf3fd94880111ef1a07e6925，协议64。中央分隔带节点基准、四档滑块、立交汇流箭头及封闭区无显式框架空指针修复保留。用户新截图已证明时间预算不是解决预览问题的办法，不把该旧版真实建造测试通过当作当前性能问题结案。历史修改与测试见FINAL_VERIFICATION_430.md、FINAL_GAME_TEST_430.md及FINAL_VERIFICATION_429.md。
