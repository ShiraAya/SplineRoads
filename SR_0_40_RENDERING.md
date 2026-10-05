> **历史文档：0.40.1 已修订 AUTO 为“光影包实际启用才用 terrain”，并重做分块队列；当前行为以 [0.40.1 修复说明](SR_0_40_1_FIXES.md) 为准。以下保留 0.40 的原始设计与验证记录。**

# SR 0.40 渲染、光影接入与复测说明

## 1. SR 道路原来属于什么 render

0.39 路面主要由 `RoadRenderer` 在 `AFTER_BLOCK_ENTITIES` 独立绘制，顶点格式是 `NEW_ENTITY`，使用实体类着色器和独立纹理。它不是“每个可见面属于普通方块 BakedModel”的地形渲染路径。能够受光／雾影响，不等于必然能获得光影的地面材质、水坑和雨天反射。

0.40 增加**混合地形后端**：沥青顶面及标线通过 `BakedModel`、方块图集和 `RenderType.solid()` 提交区块地形；桥侧、桥底和道路设施仍走 VBO。曲线和坡道保持连续面，不变成一格一格的阶梯。具体实现见 `RoadTerrainMesh`、`RoadTerrainModels`、`RoadRenderer`。

本版未内置光影，不新增假水反平面，不把沥青材质标记成水。实际地形路径会让光影获得地形几何和对应方块身份，但反射／积水是否启用仍取决于该包。

## 2. 配置

首次启动 0.40 后，Forge 客户端配置默认位于：

```text
.minecraft/config/splineroads-client.toml
```

使用独立游戏目录时，以启动器为该实例配置的实际 `config` 目录为准。建议退出游戏再编辑。

```toml
[rendering]
    surfaceBackend = "AUTO"
    terrainSectionsPerFrame = 4
    vboUploadMicros = 900
```

| 选项 | 准确行为 |
| --- | --- |
| `AUTO` | 检测到 **Oculus 或 Iris 模组已安装**就选择地形；否则使用 VBO。这不是当前光影包是否开启的检测，因此装着 Oculus 但关闭光影时也可能显示 terrain。 |
| `TERRAIN` | 显式使用地形模型，适合无光影对照测试或没有命中 AUTO 的环境。不是“强制所有光影产生水坑”。 |
| `VBO` | 回退到原独立渲染路径，仍包含本轮逐顶点光照及状态切换优化。雨水材质兼容问题可能仍在。 |
| `terrainSectionsPerFrame` | 每帧最多发布的 16×16×16 子区段数，默认 4，范围 1–32；还受软时间预算约束。它不是硬性帧时间保证，也不控制其他模组区块重建。 |
| `vboUploadMicros` | 基础设施 VBO 上传的软预算，默认 900 微秒，范围 200–5000；单次不可分割驱动调用可能超过预算。 |

路网首次显示或大批变更时，地形模型可能逐批出现，以避免同一帧处理所有路面。降低预算倾向于更平稳、显示完成更晚；提高预算倾向于更快出现、可能更卡，实际效果要在当前光影和显卡上对照。

AUTO 不检测 OptiFine。本次没有真实 Oculus／Embeddium／OptiFine 环境验证。没有把“检测到模组名字”当成兼容已经通过。

调试界面原有 SR 行后新增类似：

```text
SR surface: terrain, 120 terrain sections, 3 pending
```

这里的区段是 SR 已发布的模型区段，不是世界总区块数。原 SR CPU 行也不等于完整游戏帧耗时，更不是 GPU 时间。

## 3. 光影包材质映射

地形模型挂在原有 SR 方块上：

```text
splineroads:road_collision
splineroads:road_infill
splineroads:road_node
```

可用于资源包覆盖的路面纹理：

```text
assets/splineroads/textures/block/terrain_asphalt.png
assets/splineroads/textures/block/terrain_paint.png
```

这些纹理来自原工程的 `textures/road/asphalt.png` 和 `white.png`，没有改变其内容。原 VBO 纹理保留。资源包只覆盖旧 VBO 路径时不会自动覆盖新方块纹理路径。

Shader pack 的 `block.properties` 数值类别由**各包自身**定义，没有通用的“沥青反光 ID”。需要专用映射时，应该先读该包再将上述 SR 方块映射到合理的不透明地面类别，不能随意复制某包的数字到其他包，也不要把道路设为水／玻璃／金属以强迫出现反光。存在原地形填充面的 SR 单元还可能显示填充材质，映射整个 block ID 时需一并检查侧面效果。

如果包主要依赖 PBR 贴图而不是程序化雨水，还需要其所支持的粗糙度／法线等材质资源。**本版没有臆造通用 PBR，也没有修改你未提供的光影包。**

## 4. 编译与启动

保持 Minecraft 1.20.1、Forge 47.4.20、JDK 17，首次依赖下载需要联网：

```powershell
.\gradlew.bat compileJava compileGameTestJava validateRamp39 validateLaneRampWorkflow validatePerformance40 jar
```

正常完成后，模组文件应位于 `build/libs/`。本交付不包含这个 JAR，因为交付环境连 Gradle 下载域名都无法解析。源码中包含测试适配器，但它们位于 `tools/`，没有加入生产 sourceSet，也不会被打进模组 JAR。

Linux／具有 Bash 的环境可运行离线分层测试：

```bash
bash tools/test_perf40.sh
```

用完整的未修改 0.39 源码目录作微基准与几何对照：

```bash
bash tools/compare_perf40.sh /path/to/SplineRoads-0.39.0-alpha
```

这些离线命令用明确的测试适配器隔离依赖，不能取代前面的 Forge 编译或 Minecraft 实测。

## 5. 需要实际客户端完成的验收

以下均**尚未在交付环境执行**。先使用世界副本，保持同一渲染距离、地点、视角、天气和其他模组配置。

| 场景 | 检查结果 |
| --- | --- |
| 无光影：VBO 与 TERRAIN 各一次 | 沥青／标线／曲线／坡道连续，无粉黑贴图、裂缝、路面消失或阶梯化。 |
| 路面贴地、斜坡、填充块和端点位置 | 没有填充顶面闪烁；道路端点编辑方框仅在原来允许的条件下显示，模型不留下方孔。 |
| 白天、夜间、拿火把／固定路灯 | 顶点亮暗连续、坡面方向合理；光源增删后原 VBO 设施和地形路面均更新。 |
| 桥面、桥下、隧道、遮雨棚内外 | 阴影、遮挡、天空光合理；不能只看露天一段就判所有照明正确。 |
| Oculus／Embeddium＋实际光影包，雨天与晴天对照 | 确认 HUD 选择 terrain，等待队列完成；水坑／水反按包实际结果记录，和 CB／原版方块并排比较。 |
| 退出到菜单后换世界、重启后第一次进存档 | 不读前一个世界的道路、光照、标牌或缓存；记录进世界总时长，不能只记录 SR 函数时间。 |
| 沿长道路飞行使区块加载／卸载 | 已加载区块路面及时出现，卸载的模型数据不无限累积，不能跨区块留洞。 |
| 建造／编辑／删除／取消／资源重载／切换后端 | 没有旧标线或路面复活；旧 UV 不留存，不重复显示地形与 VBO 路面。 |
| 原 0.39 的整车道分离、分叉、补回组合 | 路面、碰撞、标线和护栏同时正确，编辑／删除／真实保存重进后不破坏连接。 |
| 专用服务器和多人 | 同时升级客户端、服务器；检查其他玩家看到的路面更新和碰撞一致性。 |

地形通道可能增加顶点和模型内存，同时使地形材质、区块裁剪与引擎灯光可用；不保证在每个光影设置上都比大批次 VBO 有更高 FPS。保留 VBO 是为了便于回退和对照，不是为了把失败隐藏掉。

## 6. 慢操作记录

新加入 `core/RoadTimings`：默认输出超过阈值的慢编辑／载入；可在启动器的 JVM 参数中添加：

```text
-Dsr.profile=true
```

然后在启动器控制台中搜索 `SR edit`、`SR connect`、`SR edit_node`、`SR load`。是否同时收进 `latest.log` 取决于启动器／日志桥接。编辑记录包括 `topology`、`structures`、`edit_raster`、`sidewalks`、`local_collision`、`validation_commit_sync` 阶段，便于知道慢在算线、桥墩、碰撞、世界提交还是同步。

`SR load` 从已交给模组的 NBT 对象开始计时，不包括此前压缩文件的磁盘读取与解压。计时是诊断入口，不是“卡顿全部修好”的证明。

## 7. 外部接口依据（与 SR 源码事实分开）

- Forge 1.20.x BakedModel／ModelData 扩展接口：
  https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.20.x/src/main/java/net/minecraftforge/client/extensions/IForgeBakedModel.java
- Forge 1.20.x ModelEvent，模型注册修改与烘焙完成阶段区别：
  https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.20.x/src/main/java/net/minecraftforge/client/event/ModelEvent.java
- Forge ModelData 不可变／多线程数据约束：
  https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.20.x/src/main/java/net/minecraftforge/client/model/data/ModelData.java
- Iris 地形 `mc_Entity` 及 `block.properties`：
  https://shaders.properties/current/reference/attributes/mc_entity/
  https://shaders.properties/current/reference/miscellaneous/block_properties/

上述文档用于确认接口与地形身份语义，不能替代真实 Forge 47.4.20、Oculus 和某个光影包的运行结果。没有审查 CB 源码；“CB 道路有雨水效果”是用户的实测反馈，不据此臆测 CB 内部实现。
