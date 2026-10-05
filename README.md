# Spline Roads 0.40.8-alpha — P1隧道边界与道路接缝

Minecraft Java1.20.1 / Forge47.4.20 / Java17。工作分支chat/sr-0402，沿用用户编译修正后的SR.zip基线。

**P1《问题2》全部24项优先；P2《问题3》terrain四项与多线程，必须等全部P1处理后再开始。** [优先级](docs/PRIORITIES.md) · [当前逐项状态](docs/issues/problem2-batch2-status.md)。

本版处理Q2-03额外地形开挖、Q2-04入口外侧原地形保留、Q2-07拱顶整数层越界及Q2-05地面/隧道共同接缝。只修已定位代码路径，不能称所有分数边界缝隙或原存档症状均结案。隧道中央绿化转护栏，黄线保留，双方保留同一车道轴线；无关旧功能保持。

最终生产及已下载核验源码/JAR对应 `3be551cafc56700b63883586d888beda17f217c4`。完整Forge构建与旧/新回归通过；[实际校验](docs/checkpoints/tunnel415-verified.json)、[PROGRESS](PROGRESS.md)。本README在构建后的文档提交，精确构建源码ZIP不伪装包含后续文字。

```powershell
.\gradlew.bat compileJava compileGameTestJava jar
```
```bash
bash tools/check_tunnel415.sh
```

测试适配器不入JAR。未执行真实Minecraft客户端/GPU/光影、方块写入、水流传播、二进制保存重进或多人。旧设施可能需编辑更新，有原始材料记录才可恢复，未知旧地形不凭空填回。

存档36、网络协议54保持；先备份世界，客户端与服务端同步替换。保留任意所选车道TEMPORARY和DETACH原限制，当前目标汇入新语义等余下17项P1仍待处理。一次性历史apply脚本禁止在后续源码重跑。
