# SR 0.40.1-alpha 验证记录

## 结论与环境

本轮执行了离线生产核心、生产规划器、地形发布桥及源码语法验证；结果通过。环境为 OpenJDK 21.0.11，`javac --release 17`，UTF-8，最多使用两个处理器的测试 JVM。Minecraft／Forge 类型由 `tools/` 下明确的测试适配器提供；**没有真实世界写入、GPU、Oculus、Embeddium 或用户存档实测**。全部测试替身只在离线脚本中使用，不加入生产 JAR。

完整 Forge 命令已经实际尝试：

```bash
bash gradlew --no-daemon compileJava compileGameTestJava validateRamp39 validateLaneRampWorkflow validatePerformance40 jar
```

wrapper 下载 `gradle-8.8-bin.zip` 时发生 `java.net.UnknownHostException: services.gradle.org`，退出码 1；编译任务尚未开始。不能将离线通过写成 Forge 编译通过，也没有编译好的模组 JAR。

## 有针对性的回归

| 验证 | 实际结果 | 不代表什么 |
| --- | --- | --- |
| 上传源码基准复现 | 使用同一旧匝道端口／路径失配夹具，在远处建普通道路，原 `LaneTopology` 被旧匝道生成错误阻断；错误包含 B 前后 88 格。 | 不是用户截图坐标或其原存档逐点复现。 |
| `UnrelatedRamp401Probe` / `LocalEdits401Validation` | 普通道路远处创建、修改、删除通过；普通道路 Link 仍为空；没有把远处旧匝道加入事务。11 条追加断言覆盖真实依赖移动拒绝、局部真实碰撞拒绝、无损失败、支撑更新不重规划、旧匝道自身删除、无关路口组不处理、使用中自动点保护。 | 内存规划不是 `RoadData.replaceBatch` 的真实方块提交、存盘、网络权限验证。 |
| `ShaderBackend401Validation` | 219 条断言通过；可用／缺失 API、旧命名空间、关闭 pack、开启 pack、异常回退、阴影阶段、图集失效、显式覆盖及重复稳定帧。 | pack 状态由假 API 控制；没有真实光影编译、阴影、运行中热切换效果验收。 |
| `TerrainStreaming401Validation` | 25 条断言通过；同道路三个 chunk，200 次往返视区变化：共烘焙 3 tile，缓存命中 199 次，chunk 事件导致的整路失效为 0。重复通知去重、停止后队列清零、道路编辑重新烘焙、旧任务取消／删除、同 ID 来源更新均通过。140 tile 卸载后缓存保持 128 以下且不超过 quad 上限；超出 quad 上限的单个瓦片会被淘汰，多个道路共享同一区段时删除其中一个不影响其他贡献。 | chunk 生命周期由适配器控制；不是实际飞行 FPS、真实区块线程或显存测量。 |
| `TerrainChunk401Validation` | 75,455 条断言通过；12 组含负坐标、斜面、标线的分块结果与完整 CPU 裁面逐顶点相同，完整覆盖一致；65,536 格长面只处理指定 chunk 的 32 个 cell。 | 保持几何与归属，不等于 GPU 光照表现已经验证。 |

`ShaderBackend401Validation` 故意让 API 抛一次异常，检查安全回退；日志中的 `deliberate API failure` 及一次 WARNING 是预期注入，不是最终测试失败。

## 原有回归继续执行

`EndpointV2Validation` PASS；`LanePointSnapValidation` 742；`LaneInteractionValidation` 254；`LaneRampV2Validation` 2,836；`LaneRampWorkflowValidation` 9,190；`Ramp39Validation` 16,231；`Ramp39ModelValidation` 377；`Ramp39CompoundValidation` 2,767；`Ramp39WidgetValidation` 12；`Performance40Validation` 200,936；`Index40Validation` 175,398；`Terrain40ModelValidation` 87。

原三变二／DETACH／分叉／跨越／REPLACE 组合仍在三组尺度与旋转条件下通过内存规划；没有新增“任意建造顺序实机通过”的声明。几何摘要 64 组有效，SHA-256 仍为 `6c50d4bb328abb230584b611b61483422005b889a4829afabb95d9107b1bdae8`。

本轮模型编译还改用真实 `RampJunctions`，不再用该类的旧空适配器；但 `Interchanges.checkExternal` 的测试适配器明确拒绝未覆盖的真实组装检查，因此不能声称路口全流程实机已验收。`SourceSyntax40` 对 156 个生产 Java 单元执行语法解析；语法解析不是完整链接或 Forge API 类型检查。生产 `RoadRenderer`／`RoadData` 的完整符号检查仍依赖未完成的 Forge 构建。

## 原始证据

`validation-results/0.40.1/` 包含最终套件日志、分项日志、原包失败探针日志和真实 Gradle 尝试日志。离线重跑：

```bash
bash tools/test_hotfix401.sh
```

构建最终记录源码的在线命令（JDK 17，Windows PowerShell）：

```powershell
.\gradlew.bat compileJava compileGameTestJava validateRamp39 validateLaneRampWorkflow validatePerformance40 jar
```

原有性能微基准随回归运行，保留原始轮次；本轮没有据此宣称游戏帧率提升百分比或启动存档总耗时。

## 应当进行的实机复测——当前均未执行

| 场景 | 通过条件 |
| --- | --- |
| AUTO，无光影模组 | HUD 为 VBO，原有道路和普通编辑可用。 |
| AUTO，安装 Oculus 但 pack 关闭 | HUD 为 VBO；不因安装状态使用 terrain。 |
| 同一世界开启 pack／再关闭 | 切为 terrain／再回 VBO；允许一次模型刷新，完成后无持续重置、残留双层路面或洞。 |
| pack 编译失败／重载资源 | 根据实际 pipeline 状态回退；不继续显示旧图集或旧任务生成的模型。 |
| 重复走同一路段 | 在仍驻留且缓存未淘汰的范围内，不反复产生整条道路 mesh 构建；tile 队列可短暂增加并收敛。 |
| 飞入新密集路网、再返回 | 首次加载允许新工作；记录队列、SR CPU、帧时间，而不是只看瞬时 FPS。 |
| 在用户原世界远离问题匝道建、改、删普通路 | 不再触发那条无关匝道的坡道／B 搜索错误。 |
| 真正修改匝道宿主或把新路穿进已有匝道 | 仍重建真实依赖或拒绝冲突；不能通过绕过净空伪装修复。 |
| 删除、保存退出、重新进入；多人同步 | 无旧 tile 复活、无连接丢失，无错误解锁。 |

用户提供的截图没有完整存档、光影包、显卡驱动和对应性能采样，因此不凭截图承诺所有剩余卡顿都来自这一个队列原因。本轮交付消除了源码中已确认的过粗重建路径；其他真实世界／GPU 问题仍需对应复测证据。
