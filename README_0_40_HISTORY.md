# Spline Roads 0.40.0-alpha — 性能与地形渲染改造

Minecraft Java 1.20.1 · Forge 47.4.20 · Java 17。基于本对话 0.39 自由匝道版直接修改。

**完整源码测试版，不含编译好的模组 JAR；完整 Forge 编译因 Gradle 下载域名无法解析而未完成，未做 Minecraft／光影实机验收。**

## 本版文档

- [生成、载入、渲染和光照的实际修改](SR_0_40_CHANGELOG.md)
- [验证范围、原始日志与微基准](SR_0_40_VALIDATION.md)
- [地形后端、配置、光影原理与复测](SR_0_40_RENDERING.md)
- [本轮修改文件清单](SR_0_40_CHANGED_FILES.txt)

道路几何弱身份缓存、按需碰撞栅格、局部位置／净空查询、端点初始化索引化，以及地形模型＋VBO 混合渲染。路面及标线可进入普通区块地形通道；桥侧和设施保留原渲染器。

`config/splineroads-client.toml` 中 `surfaceBackend = "AUTO"` 默认在检测到 Oculus／Iris **已安装**时启用地形，未安装时用 VBO；可显式设置 `TERRAIN` 或 `VBO`。**地形通道接通不等于任意光影必有水坑／水反。**

## 构建

JDK 17，首次需要联网下载依赖：

```powershell
.\gradlew.bat compileJava compileGameTestJava validateRamp39 validateLaneRampWorkflow validatePerformance40 jar
```

离线核心／模型适配器测试（不是 Forge 或 GPU 验证）：

```bash
bash tools/test_perf40.sh
bash tools/compare_perf40.sh /path/to/unmodified/SplineRoads-0.39.0-alpha
```

测试适配器位于 `tools/`，不进入模组 JAR。先用世界副本测试，客户端、服务器建议同步更新。

## 0.39 功能与边界保留

先选车道点 A 汇出，再选车道点 B／路口中心汇入；附属端点不参与匝道连接。整车道分离、补入空位、定向左转／回环、高程选项及精确／弹性 B 保留。

本轮没有新增任意内部车道分离或完整收费广场。原有“单向边缘车道分离、同一实际路段同槽位补入”等限制仍见 [0.39 操作说明](RAMP_0_39_BUILD_AND_TEST.md)。本轮存档版本 34 和网络协议 52 与 0.39 一致。

旧版历史入口：[0.39 README](README_0_39_HISTORY.md)；其“本版”一词仅指历史版本，不取代本页和 0.40 验证记录。
