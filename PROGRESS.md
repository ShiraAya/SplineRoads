# SR431 WIP: actual algorithm and construction optimization

Version 0.40.19-alpha-perf1, protocol remains 64; only SR-0.40.15.
User rejected timeout-only handling. Replaced interactive 8/12/4/6-second cutoffs with cancellation and stale-result checks, without changing geometry protections.
Measured old hotspots: corridor contact-by-all-samples scan; clearance preparation; repeated pure collision rasterization between preview and construction.
Optimizations: adaptive interval/sweep corridor, exact triangulation and protected-deck caches, short/long path hybrid spatial checking, flat-box compaction, bounded full-equality pure raster reuse.
Local equivalence/core/model runs passed. Full Java17 Forge and same-runner old/new real preview/build verification are now running; NO claim of completion or original-user-world acceptance.
The new GameTest adds the actual readonly previewAssembly stage before build and checks no preview NBT/revision mutation; baseline and new use identical fixtures.

---
## Historical checkpoint
# 当前：SR430 / 0.40.18-alpha 已编译，最终两项真实建造测试通过

工作分支仅SR-0.40.15。安装JAR与完整源码的精确提交32cee0fae8a03726bf3fd94880111ef1a07e6925，协议64；后续交接提交仅为文档。main等其他分支未修改。

用户《0.40.17问题.docx》四项已有生产修改：双向道路以中央分隔带为端点参考；车道滑块实际吸附1/2/3/4四档；汇流复用立交箭头；最严重的卡住问题以后台纯几何规划、主线程安全快照/复核、8秒计算预算和12秒排队总截止、显式取消与过期结果丢弃、车道恢复空间索引复用处理。最终世界写入仍在主线程，未关闭几何/世界保护，截止不是任意第三方或单次区块访问阻塞的强制中断。

真实TEMPORARY/MERGE建造另发现封闭区端部路缘frameA为空导致NullPointerException，已在a2024f68修复并增加24项生产几何回归；相同失败夹具现在通过，没有更换道路形状或跳过保护。

最终Actions37511903071：build作业112434904958 SUCCESS；minecraft作业112434904263 SUCCESS。真实Minecraft两项均实际执行并通过：TEMPORARY/MERGE规划3432.999455毫秒、建造4520.943741毫秒，碰撞/预约/删除恢复通过；ADD规划49.762899毫秒、建造1253.919726毫秒，实际3->4、Mojang NBT保存加载、删除恢复3通过。不是用户原存档/FPS测量，不据此宣传91.3秒的加速倍数。

完整旧回归链在53bd527a通过；最后端部修复后的32cee0fa重跑基础core/model、427/428/429/430、Closure24、Scheduler12并实际Java17 Forge编译及上述真实GameTests。模型适配器测试不冒称真实网络/客户端渲染。

最终安装源码产物11435518388、真实Minecraft产物11435688917均已下载核验。JAR SHA256 d7a31a08f45f187bbc982e3846ddf94a0e7a8064074ddd23fba839eebc0c284f；source ZIP SHA256 6d19360ac3842817c65b01ec70bb0ca3d6037dbda073874adfc80b9f0e133c41。ZIP提交注释、Manifest/mods.toml版本、协议64和621个src/main文件一致性已核对。

仍未取得用户原存档/latest.log，不能证明原卡住的唯一原因或所有原场景已修复。未做真实客户端GUI/GPU、车辆、多人回执验收，未展开P2渲染/光影多线程；既有显式曲线和被旧版本移动的实体端点不自动全存档迁移。

全部源码已在交接前保存，随后仅收取已经启动的验证结果和归档，无继续扩展功能或后台开发承诺。详细修改与历史见docs/checkpoints/FINAL_VERIFICATION_430.md；最终成功结果补记见docs/checkpoints/FINAL_GAME_TEST_430.md。历史0.40.17-hotfix1详见FINAL_VERIFICATION_429.md。
