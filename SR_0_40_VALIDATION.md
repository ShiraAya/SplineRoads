# SR 0.40.0-alpha 验证记录

## 1. 交付状态和环境

实际修改基线为本对话上一轮 0.39 源码。当前交付包含 0.40 源码、测试代码、明确隔离的离线依赖适配器和原始日志，**不包含编译好的模组 JAR**。

执行环境：Linux 容器，OpenJDK 21.0.11；离线编译使用 `javac --release 17 -encoding UTF-8`。Java 运行参数为 `-Xmx1500m -XX:ActiveProcessorCount=2 -Djava.awt.headless=true`，UTF-8。目标工程仍配置 JDK 17、Minecraft 1.20.1、Forge 47.4.20。

真实 Forge 构建实际尝试了：

```bash
./gradlew compileJava compileGameTestJava validatePerformance40 --no-daemon
```

结果：在下载 Gradle 8.8 时发生 `java.net.UnknownHostException: services.gradle.org`，退出码 1，尚未进入 Java 编译任务。因此**不能声称完整生产源码已通过 Forge 类型检查，更不能声称已实机或光影通过**。日志：`validation-results/performance040/forge-build-final.log`。

最终生产 Java 语法检查为 155 个文件通过，命令：

```bash
java tools/SourceSyntax40.java src/main/java
```

它只调用 JavacTask.parse，未解析外部符号／API，明确不是完整编译。本轮最终人工复查修正了两个计时接入点的变量作用域，并同步更新模组元数据版本和会话缓存清理。原始语法日志一并保留。

## 2. 已运行的分层验证

入口 `tools/test_perf40.sh` 包含以下验证。`tools/compare_perf40.sh` 使用独立、未修改的 0.39 解压目录，先验证双方，再对比完全相同的微基准程序。比较脚本最终执行结束、退出码 0，完整日志位于 `validation-results/performance040/comparison.log`。

| 层次／入口 | 结果 | 准确覆盖 |
| --- | --- | --- |
| EndpointV2Validation | PASS | 端点、连续道路、标线范围等生产核心。 |
| LanePointSnapValidation | 742 checks PASS | 车道位置核心。 |
| LaneInteractionValidation | 254 checks PASS | 车道点交互状态核心。 |
| LaneRampV2Validation | 2,836 checks PASS | 匝道路径及方向核心。 |
| LaneRampWorkflowValidation | 9,190 checks PASS | 原匝道工作流核心。 |
| Ramp39Validation | 16,231 checks PASS | 0.39 分离／补入、左右转、高程、净空等核心回归。 |
| Ramp39ModelValidation | 377 checks PASS | 实际 LaneRamps、LaneTopology、RoadRecord 等，使用明确的内存世界／NBT 适配器。 |
| Ramp39CompoundValidation | 2,767 checks PASS | 三变二、分离、分叉、I 上跨继续车道、II 上跨④、补回、重算和删除；两种尺度，其中一种有旋转／平移。不是实景建造。 |
| Ramp39WidgetValidation | 12 checks PASS | 原界面逻辑，测试 widget，不是真正 Minecraft 窗口。 |
| Performance40Validation | 200,936 checks PASS | 实际弱身份缓存；精确地形面裁剪；正负坐标、斜坡、面积守恒、加载区块过滤；表面实际所属碰撞单元。 |
| Index40Validation | 175,398 checks PASS | **实际生产 RoadIndex／DeferredMap／RoadRaster**；新局部查询与全量碰撞、道路列、人行道顶面、隧道／普通道路净空的对照，以及延迟物化、缓存失效。 |
| Terrain40ModelValidation | 87 checks PASS | 实际 RoadTerrainModels／RoadClientConfig 的模型状态逻辑，配套显式 API 适配器；顶点布局、UV、法线、非 fullbright、渲染层小偏移、整数高度归属、删除、待办覆盖、旧 ModelData 清理、资源重载和回退。**不是 GPU／Oculus／Embeddium 测试。** |

重要区别：原有 model-test-support 中的 RoadIndex 是测试模型；本轮 Index40Validation 另行编译真实 RoadIndex，不能把两个测试层混淆。后者仍使用测试用 BlockPos、ChunkPos、AABB 等，不运行 Minecraft VoxelShape 优化器和真实方块世界。

所有 `tools/*-support` 都是离线适配器，不属于生产 sourceSet，不打入模组 JAR。它们无法证明真实 API 签名完全匹配，也不模拟显卡或光影。路标目录在离线核心测试中仍单独隔离，未以本次测试宣称路标目录重验通过。

## 3. 几何输出对照

同一 `Geometry40Parity` 可执行程序，分别以**完整原版 0.39 核心**和当前核心为 classpath。覆盖 4 种样式 × 4 种路径模式 × 2 种坡度 × 2 种原点，共 64 组；包含旋转无关的直线／曲线／圆弧／圆环、大坐标平移。

比较每个样本的坐标、方向、站距、半宽、道路包围盒及长度的 raw double 位流；原版和新版均成功 64 组、拒绝 0 组，摘要相同：

```text
6c50d4bb328abb230584b611b61483422005b889a4829afabb95d9107b1bdae8
```

`geometry-039.log` 与 `geometry-040.log` 比较一致。这里证明的是这些代表性输入在网格补偿移出循环、固定样式缓存和直线快速路径之后没有改变输出，**不是所有可能道路形态的穷举证明**。

## 4. 同条件 CPU 微基准

程序：`tools/perf40-validation/com/sora/splineroads/world/Performance40Benchmark.java`。构造 256 条彼此独立的 64 格直线道路；相同 JVM 参数、资源、适配器，几何查询先预热两轮，每个统计项目采集五轮。

“重复 mesh”每轮为 256 × 16 = 4,096 次对已存在不可变道路记录的 `mesh()` 查询，**有意测试一轮规划中的重复查询和缓存命中**。不代表第一次生成 4,096 条路所需时间。

“自动端点初始化”每轮在已经构造好的内存道路表上调用实际 `LaneTopology.initialize()`。不包含磁盘、NBT 解压、真实方块世界、全部碰撞写入，也不包含进存档画面的完整时间。

最终配对运行的五轮中位数：

| 项目 | 原版 0.39 | 本版 0.40 |
| --- | ---: | ---: |
| 每轮 4,096 次重复 mesh 查询 | 478.473 ms | 0.713 ms |
| 256 条道路的自动端点初始化 | 127.141 ms | 63.896 ms |

双方校验和都是 `1343488.0`。没有把局部函数耗时或缓存命中优势折算成真实 FPS、TPS 或存档加载加速百分比。

原始五轮，单位毫秒：

```text
0.39 mesh:     [2732.292525, 966.249163, 478.473247, 398.414998, 452.276639]
0.40 mesh:     [0.984752, 0.954980, 0.697940, 0.712671, 0.702301]
0.39 endpoint: [1074.482478, 137.751831, 127.141340, 121.177306, 118.924810]
0.40 endpoint: [142.177167, 63.895989, 69.022253, 26.132368, 44.934049]
```

容器／JIT／GC 的波动明显，保留全部五轮而不是只选最小值。其他回归运行中的微基准值也在完整日志里，其差异不被隐藏；最终表采用 `benchmark-039.log` 与 `benchmark-040.log` 的同一次配对比较，不混用不同运行中最好的一轮。

## 5. 没有完成的验证

- 依赖齐全的全工程 Forge 编译与真实 JAR。
- 真正 Minecraft 客户端、区块编译线程、专用服务器、网络多人同步。
- 用户原存档的首次读取与进入总时长，实际道路／立交生成和编辑墙钟时间、FPS／TPS／GPU 时间。
- 真实磁盘 NBT 保存、退游戏、重启、重进的全流程。
- Oculus、Embeddium 或其他渲染模组的实际组合。
- 任一个光影包在晴雨、露天／桥下／隧道中的积水、水反、阴影和材质外观；与 CB 道路的实景对照。

待执行步骤见 `SR_0_40_RENDERING.md`。本版提供真实地形模型实现与回退，不把“代码接口接入”写成“所有光影兼容完成”。

## 6. 打包验收

完整包保留原工程资源和历史说明，不包含 `.git`、`.gradle`、临时 `build` 或测试世界。本轮测试支持代码位于 `tools`，与生产代码隔离。补丁相对干净 0.39 基线生成，包含新增源文件和方块图集纹理。

打包后的补丁应用、逐文件比较和压缩包 CRC／SHA256 检查结果由交付旁的 `SR_0_40_PACKAGE_CHECK.txt` 记录；其检查结果只覆盖打包一致性，不改变以上未验证边界。
