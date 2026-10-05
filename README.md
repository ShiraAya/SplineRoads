# Spline Roads 0.40.4-alpha

Minecraft Java 1.20.1 / Forge47.4.20 / Java17。以用户修正编译后的SR.zip为基线，在chat/sr-0402连续保存。

**TEMPORARY保留车道分离适用于任意所选车道，包括内侧/中间/外侧与单车道；DETACH整车道分离原有适用范围不变。** 不是一次同时选择所有车道。主路原槽位暂时关闭，实际匝道及结构让出通行空间后渐变恢复；其他车道不因此被挪走。跨多个实际路段自动恢复及跨车道数变化接缝仍未支持，单纯缺少净空不会强行建造。

0.40.3已修正隧道侧墙/拱肩缺口与开挖空气预留；0.40.4修正独立渲染距离策略、隧道中央过渡、实体人行道材质深度模式，增加上层人行道结构碰撞和隔音墙混凝土基座。完整状态见 [13项问题状态](docs/issues/2026-10-05-resolution-status.md)。这不是13项全部修复完成版。

[本轮修改与验证](docs/checkpoints/2026-10-05-visibility-furniture.md) / [任意车道与隧道检查点](docs/checkpoints/2026-10-05-any-lane-tunnels.md) / [进度](PROGRESS.md)。历史apply脚本是一次性迁移，不可重新运行到最新源码。

实际生产提交已通过GitHub Actions的compileJava、compileGameTestJava、jar/reobfJar，且已下载校验源码/JAR。**没有真实Minecraft客户端、GPU/光影、真实世界写入/存盘重进、多人或FPS验收。GameTest类编译不是实机测试。**

存档版本36、协议54。客户端与服务端同步更新；先备份世界并在副本测试，勿用旧版读取新版保存的世界。新结构可能需要道路编辑更新来重新生成，旧开挖地形不保证自动填回。

```powershell
.\gradlew.bat compileJava compileGameTestJava jar
```

```bash
bash tools/test_transition402.sh
bash tools/test_tunnel406.sh
```

本次针对性回归见只读.github/workflows/sr-checkpoint.yml，工具目录测试适配器不进入生产JAR。

AUTO仍为无光影VBO、实际启用光影terrain。额外休眠缓存inactiveVboCacheMiB与terrainCacheMiB默认各64MiB估算预算；不等于总显存/RAM上限。有效缓存复用，改路/资源/格式/世界改变及淘汰仍正确失效，不承诺光影管线切换时游戏自身无区块重建。
