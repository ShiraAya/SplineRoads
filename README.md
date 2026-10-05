# Spline Roads 0.40.5-alpha

Minecraft Java1.20.1 / Forge47.4.20 / Java17。以用户修正编译后的SR.zip为基线，在chat/sr-0402逐阶段保存。

**隧道中央仅黄线/护栏：选绿化自动转护栏，不在隧道里生成土和树叶。** 保留断面预留宽度与车道轴，不在洞口突然挪动行车线。这个用户最新要求覆盖0.40.4洞内绿化的错误实现；原混凝土中央分隔不是被判定为错误的设计。

本版另修路口盲道的反角内凸与错误内缩折返；桥墩按实际主柱近邻/高度判断并使用原候选挪动，不再只靠连接ID和道路首点高差。原过渡、任意所选槽位TEMPORARY临时取消后恢复、DETACH原适用范围、隧道壳体/空气与双渲染后端缓存修复保留。

[本轮细节](docs/checkpoints/2026-10-05-0405-tactile-piers.md) / [13项问题状态](docs/issues/2026-10-05-resolution-status.md) / [当前进度](PROGRESS.md)。一次性apply脚本保留为历史，不可对最新源码重跑。

生产提交42479eb8的GitHub Actions run37331140199已经完整构建success。最终带附加连续性测试/文档的提交仍须以对应CI和下载核对记录为准。**没有实际Minecraft客户端、光影/GPU、真实世界放置/保存重进或多人验收。** 计数是循环几何检查，不等于同数实机案例。

存档36、协议54不变；客户端服务端同步更新，先备份世界。已存设施可用道路编辑更新重新规划；旧开挖地形不保证自动填回。

```powershell
.\gradlew.bat compileJava compileGameTestJava jar
```

```bash
bash tools/check_release0405.sh
```

该脚本同时运行原回归、新盲道/护栏/桥墩测试和完整Forge构建。工具适配器不进入生产JAR。

建造性能本版完成更细阶段计时，可加JVM参数`-Dsr.profile=true`；世界/GPU写入没有移到后台线程，尚未完成完整并行提速，未测实际FPS。U02当前新沥青资产来源仍缺少用户原文件，资源许可保持。AUTO无光影VBO、实际光影terrain及有界缓存行为不变。
