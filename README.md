# Spline Roads 0.40.2-alpha

Minecraft Java 1.20.1 / Forge47.4.20 / Java17。

R1过渡标线、R2双向外侧整车道分离、R3暂时关闭后按真实通行空间恢复、R4有界VBO/terrain热缓存的生产代码已保存。GitHub Actions实际通过compileJava、compileGameTestJava、jar/reobfJar。**尚未进行真实Minecraft客户端、光影/GPU、存档重进或多人验收。**

本轮详细说明与边界见 [R3/R4检查点](docs/checkpoints/2026-10-05-R3-R4.md)。用户七页《问题.docx》的13项问题全部登记在 [新增问题](docs/issues/2026-10-05-user-report.md)，没有把登记等同于修复。材质与线程的初步核查见 [核查说明](docs/issues/2026-10-05-triage.md)。

**存档版本35、协议53，客户端与服务器同步更新；先备份并在副本测试，不要用旧版打开新版保存的世界。** 原BRANCH保持原车道直行分流；新建保留车道分离是单独TEMPORARY模式。

```powershell
.\gradlew.bat compileJava compileGameTestJava jar
```

```bash
bash tools/test_transition402.sh
bash tools/test_warm404.sh
```

后者包括原有回归、R2/R3场景、真实terrain缓存+假区块事件测试及抽取VBO方法的假资源句柄生命周期检查。工具目录的适配器不进入模组JAR。完整Forge编译与上述离线测试分别报告。

`surfaceBackend = "AUTO"`：无光影VBO，实际启用光影terrain。额外休眠缓存`inactiveVboCacheMiB`与`terrainCacheMiB`默认各64MiB估算预算；不是总显存/RAM上限。有效且未淘汰时复用，改路、删除、资源/格式改变、世界退出仍正确失效；不能保证游戏自身在光影管线切换时不重编译区块。
