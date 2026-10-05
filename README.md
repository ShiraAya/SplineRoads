# Spline Roads 0.40.7-alpha — 《问题2》P1首批修复

Minecraft Java1.20.1 / Forge47.4.20 / Java17。分支chat/sr-0402，沿用用户编译修正后的源码基线。

**P1：《问题2》24项全部。P2：《问题3》terrain四项与多线程，必须等所有P1处理完再开始。** 见[优先级入口](docs/PRIORITIES.md)和[逐项现状](docs/issues/problem2-batch1-status.md)。

本版处理Q2-02隧道外侧部分壳体保水/洞内干燥、Q2-08真实三维壳体冲突及删除校验范围、Q2-09普通道路预览/建造接缝几何一致性。不是全部P1完成，也不是允许所有原本不合法的道路强行建造。地形破坏、入口/拱顶缝隙、过渡、匝道新语义等仍待后续处理。

已下载核验的源码/JAR对应59bb21135f517da516cc4cc3910f98e938ef0ae2；真实Forge编译和新旧回归通过，细节见[PROGRESS](PROGRESS.md)和[校验记录](docs/checkpoints/problem2-batch1-verified.json)。本README是随后文档更新，精确构建源码ZIP保留构建时的提交内容。

```powershell
.\gradlew.bat compileJava compileGameTestJava jar
```
```bash
bash tools/check_problem2_batch1.sh
```

测试适配器不进JAR。完整Forge构建不等于Minecraft客户端、实际水流、世界提交、保存重进或多人验收；这些尚未运行。旧设施一般需编辑更新，不保证自动填回旧版本挖掉的地形。

存档36协议54保持；升级前备份世界，客户端和服务端同步替换。保留TEMPORARY任意车道、DETACH原限制、隧道中央绿化转护栏/黄线保留。没有开展terrain/P2专项。一次性历史apply脚本不可重跑。
