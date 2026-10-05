# SR 0.40.1-alpha 修复说明

## 基准与交付边界

直接修改用户提供的 `SplineRoads-0.40.0-alpha-performance-terrain-compile-fixed-source.zip`。输入 SHA-256：`e3f1cb8dea7ac902e1daad1951b1ca7a394293b467f9e94dd53dcb9bd4c511ec`。版本改为 `0.40.1-alpha`；存档格式 34、网络协议 52 保持不变。没有回退用户的编译修复。

本说明区分“源码已修改”“离线模型验证”“实机效果”。完整 Forge 构建在下载 Gradle 时因域名无法解析而中止，未执行 compileJava；本轮没有 JAR、用户存档复现、真实 GPU／Oculus 运行或 FPS 测量。不能把下面的测试数字视作实机帧率提升比例。

## 1. 无光影使用 VBO，有光影使用 terrain

### 原因

上传源码中，`RoadTerrainModels.beginFrame()` 将 AUTO 判为 `ModList.isLoaded("oculus") || isLoaded("iris")`。这只说明模组存在；关闭光影后仍然会走 terrain。用户图一表现为无光影环境下重复明暗块。本轮按用户要求切换后端，**没有宣称已修复“强制 TERRAIN 且关闭光影”这一组合的全部原版光照问题**。

### 修改

新增 `client/ShaderPackState.java`，按需发现公共 API，缓存反射方法，不增加必装 Iris／Oculus 依赖：

- 当前命名空间：`net.irisshaders.iris.api.v0.IrisApi`。
- 旧命名空间：`net.coderbot.iris.api.v0.IrisApi`。
- 使用 `isShaderPackInUse()`，而不是模组列表或仅查看 shader pack 文件名。
- 使用 `isRenderingShadowPass()`，避免阴影遍历驱动后端切换及重复排队。
- 接口不存在或调用失败时，AUTO 回退 VBO；失败最多记录一次警告。手动模式仍可覆盖。

| 配置／状态 | 后端 |
| --- | --- |
| AUTO；无可用光影 API | VBO |
| AUTO；模组安装，但 pack 关闭或未在渲染 | VBO |
| AUTO；pack 实际参与渲染 | terrain |
| 显式 VBO | VBO |
| 显式 TERRAIN | terrain，与光影是否启用无关 |

后端或纹理图集修订变化时清理旧任务／快照并请求一次区块刷新；稳定帧不持续重置。同一世界内切换光影，不要求重启游戏。更新保留了用户已报告可工作的 terrain BLOCK 顶点格式、图集、UV 与路面模型路径。

官方 API 核对依据（外部资料，不是 SR 自身实现）：Oculus `1.20.1-new` 的 `src/main/java/net/irisshaders/iris/api/v0/IrisApi.java` 与 `apiimpl/IrisApiV0Impl.java`。后者用当前 pipeline 是否为 `VanillaRenderingPipeline` 判定 pack 是否实际在用。公共接口说明未启用或编译失败不使用的 pack 返回 false。

参考：
- https://github.com/Asek3/Oculus/blob/1.20.1-new/src/main/java/net/irisshaders/iris/api/v0/IrisApi.java
- https://github.com/Asek3/Oculus/blob/1.20.1-new/src/main/java/net/irisshaders/iris/apiimpl/IrisApiV0Impl.java
- https://raw.githubusercontent.com/IrisShaders/Iris/1.21.1/common/src/api/java/net/irisshaders/iris/api/v0/IrisApi.java

这些接口核对及测试替身不等同于已经运行了用户的 Oculus／光影组合；切换时的真实渲染、雨水效果和过渡帧仍需实机检查。

## 2. 普通道路操作被匝道错误阻断

### 原因

**道路并没有全部被转换为匝道。** 普通道路仍没有 `LanePoints.Link`。问题在公共修改流程：`RoadData.replaceBatch()` 每次调用 `LaneTopology.reconcile()`；旧实现又遍历全图、重算或验证所有既有连接器。某条旧匝道的保存路径／端口不再满足当前生成器，即可让远处普通道路的操作返回“直接连接”“坡道空间不足”“B 前后 88 格”等匝道错误。

结构规划后的 `needsRefresh(planning)` 也检查整个 planning 集合，可能再次因为同一个无关旧端口失配进入重建。

### 修改

`world/LaneTopology.java` 计算本次修改范围：实际改变／删除的道路、受影响真实端点、车道点迁移、车道分离／补入宿主、匝道的正向依赖闭包、相关路口组。仅结构支撑重算、但路面／端口没变的既有匝道不会被误认为需要重新选线。

`LaneCrossSections` 的车道占用派生、接头开口刷新和 `RampJunctions` 的路口处理使用相应范围。`RoadData` 的后续稳定检查改成事务范围；当原保存匝道已有失配、而本次并未改变它的路面、Link 和端口时，不擅自把普通道路修改变成“修复整张路网”的操作。

实际相关的依赖仍处理：主路端口移动必须重建其依赖匝道；使用中的自动尽头点仍阻止非法续接；删除宿主仍遵守原依赖确认。新建／改变的道路真正侵入另一条保存匝道时，新增的 `LaneRamps.validateChanges()` 校验本次改变的障碍面并报告相关道路 ID，不能靠不重规划来绕过净空。

重新规划失败的关联匝道也带 ID 与“本次修改需要重建关联匝道”前缀，避免再让道路编辑器只显示无上下文的匝道错误。

**保留的边界：** 这不是自动修复所有旧存档错误的工具。实际选中或真实依赖的连接仍可能因空间、方向、缺失目标等条件而被拒绝；原有所有权、净空、地形保护与提交校验未取消。仍会扫描必要的索引／元数据来求依赖，不应理解为完全零全表访问。

## 3. 移动时数百 queued meshes 与掉帧

### 原因

上传源码的 terrain `Streaming.loaded/unloaded` 收到某个区块事件后，把该区块涉及的道路 ID 放进 `streamDirty`，再每五 tick 调用 `RoadRenderer.changed()`。

这是**整条道路几何失效**：重新运行 `RoadSurface.build()`、重做设施区域、重新准备整条道路当前所有已载入区块的 terrain 模型，并触发 VBO 上传。移动不断载入／卸载区块，便重复产生任务。停下后队列被处理完才降为 0，与用户描述吻合。

F3 的 queued meshes 是 `dirty.size() + jobs.size()`：待处理／进行中的工作数量，不是世界中道路数量、已驻留网格数量或显存用量。旧源码已经有替换、删除、reset 时关闭 VertexBuffer 的路径；仅凭该数字不能断言内存泄漏。确认的问题是过粗的失效范围和重复计算，本轮没有声称做过全进程内存／显存泄漏检测。

### 修改

保留完整道路几何与设施 VBO 的实际修改机制，**将区块流式载入与道路几何变化分开**：

1. 完整几何完成后建立不可变道路面分块索引；这里只共享面引用，不提前对远处每个方块裁面。
2. 单个区块载入时，只排该道路／区块瓦片；同瓦片的活动、等待、构建状态去重。区块事件不再调用 `RoadRenderer.changed()`。
3. 每次 tile 栅格化先把扫描范围限制到选中区块；长面不再先扫描其全部包围盒再过滤。
4. terrain 与原道路网格共享原单工作线程，最多挂起两个 tile job；优先安排近处请求，发布按区段合并并受软时间／数量预算限制。
5. 区块卸载时撤销对应活动贡献。已卸载结果用 LRU 保留，最多 **128 个瓦片、131,072 个 BakedQuad**，先达到的上限生效；这是对象数量限制，不是固定 MB 限制。
6. 在缓存仍有效时返回原区块，直接重新发布已烘焙结果。真实道路编辑、删除、资源重载和世界清理会失效相关来源，旧异步结果不能重新出现。

HUD 把整路工作标为 `road meshes queued`，并另列 `terrain tiles queued`、待发布区段及缓存数。计数反映实际状态，没有强行置零掩盖任务。

**正常现象仍保留：** 第一次看到新道路／新区块、改路、切换光影或重载资源会有队列。离开足够远，整条道路被客户端原有驻留策略移除，或瓦片超过缓存上限，回来也允许重新构建。不能承诺任意移动中所有队列永远为 0。预算为软预算，单次复杂 section 合并／区块编译仍可能超过预定时间；真实帧率须用同场景实测。

## 使用建议

保持 `config/splineroads-client.toml` 中 `surfaceBackend = "AUTO"`。此前显式强制 TERRAIN 的需要改回 AUTO；无需删除其他配置、道路或存档。首次用存档副本测试，客户端／服务器建议同时替换本版。详细回归和手动复测流程见 `SR_0_40_1_VALIDATION.md`。
