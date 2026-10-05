# SR 0.39.0-alpha：本轮验证记录

## 状态结论

**实际源码已修改，离线核心测试与明确隔离的模型适配器测试通过。完整 Forge 工程编译未完成，Minecraft 客户端／真实世界建造／真实 NBT 二进制未执行。** 没有安装 JAR，没有把离线投影、二维预览或测试数量当作实际游戏截图。

测试使用本交付同一版生产源码，JDK 21.0.11 运行 `javac --release 17`。Java 17 API 目标编译不等于已在 JDK 17 + Forge 全工程通过。原始输出位于 [validation-results/ramp39](validation-results/ramp39/)。

## A. 生产核心：6 个入口通过

| 入口 | 本轮输出 |
| --- | --- |
| EndpointV2Validation | 连续性、范围、标线隔离、身份、插值、绿化过渡、人行道墙体通过；此入口不输出数字总数 |
| LanePointSnapValidation | 742 checks passed |
| LaneInteractionValidation | 254 checks passed |
| LaneRampV2Validation | 2,836 checks passed |
| LaneRampWorkflowValidation | 9,190 checks passed |
| Ramp39Validation | 16,231 checks passed |

带数字入口合计 29,253 个断言调用，另有 EndpointV2 的 PASS。断言数受采样数量和选择路径影响，不是 29,253 种不同功能或地图。原 LaneRampV2 的基线 3,065 在本版为 2,836，是自动路径改变使采样断言循环次数变化，不是删掉测试要求。原 Workflow 测试仅补齐多级依赖测试夹具的 depth 元数据。

离线 runner 编译生产 `core`，但因 Gson 依赖不在环境中，明确排除生产 `RoadSignCatalog`，使用测试目录中的 fail-fast 替身。路标目录／Gson／该模块渲染没有被验证。没有将替身放进生产源码或正式 Gradle sourceSet。

新增验证覆盖稳定排序传递性、重叠区域至少一个路面所有者、接头标线去重、反向参考线车道映射、实际三角面净空、厚度方向、平行冲突、边缘接触、大坐标、多障碍高度平台、上跨／下穿和固定端点、坡度不足拒绝、分离／补回物理断面、保留车道中轴和旧选项默认值。

## B. 生产逻辑 + 测试适配器：3 个入口通过

| 入口 | 本轮输出 | 可证明的范围 |
| --- | --- | --- |
| Ramp39ModelValidation | 377 checks passed | 生产生成器／拓扑／记录／codec 在内存模型中的行为、选项字段和往返 |
| Ramp39CompoundValidation | 2,767 checks passed | 手绘关系的开放空间组合案例、实际板面相交层位、依赖重算／删除恢复 |
| Ramp39WidgetValidation | 12 checks passed | 生产 Screen 的选项回调、命令字段、resize 和清除状态 |

合计 3,156 次断言调用。这里不是 Forge GameTest。runner 编译真实的 `RoadRecord`、`LanePointCodec`、`RoadSignCodec`、`AttachedPointCodec`、`JunctionCodec`、`LaneTopology`、`LaneCrossSections`、`LaneRamps`，以及实际 `LanePointScreen`／`LaneRampScreen`；其余外部环境由显式测试适配器提供。

`CompoundTag` 是测试类型映射容器，验证实际 codec 写入字段再读取，但不是 Mojang NBT 字节编解码。world write、网络和 GPU 调用不伪装成功，而是抛出不支持异常。它不能证明实际类库接口、服务器方块事务、权限系统、光照、材质、行走碰撞或多人同步正确。

### 组合案例的实际输入和结果

| 空间变化 | 实际自动生成三条匝道长度（约，格） |
| --- | --- |
| 基准开放空间 | 1,289.4 / 1,291.3 / 1,054.3 |
| 纵横距离缩放至 0.5 | 644.2 / 645.2 / 526.1 |
| 0.5 尺度、旋转 0.6 弧度、平移至 100000 附近 | 644.2 / 645.2 / 526.1 |

案例先有 feeder IV，使 II 可以检测到它；然后 II 脱离原主路并跨越 feeder，I 从 II 抬升段分出并跨越主路，最后 IV 接向原空位。验收的是生成后的相交面层位、车道空位与恢复、引用和删除关系，不是手绘图同比例复刻。两端道路点及连接模式作为输入，没有手工指定贝塞尔控制点。

没有证明更紧凑尺寸、任意建造顺序、全部不同方向或高度的组合均成功。

## C. 未完成的验证

| 项目 | 状态 |
| --- | --- |
| 完整 Forge `compileJava` | 未完成：Gradle 8.8 下载域名解析失败，原始栈见 `forge-compile-failed.log` |
| `compileGameTestJava`、正式 Gradle `check`／`jar` | 未执行成功，不能拿离线 javac 代替 |
| Minecraft `ramp39-compound` 图形案例 | 已增加待执行源码入口，未运行 |
| 实际原存档截图问题逐坐标重现 | 未运行；原存档未提供 |
| 实际 NBT 二进制、保存退游重进、联机 | 未运行 |
| 所有桥墩／护栏／结构／保护区域／普通立交回归 | 未运行 |
| 大路网 FPS／真实服务器 tick 耗时 | 未测量，不给提速百分比 |

## D. 复现方式与日志性质

```sh
bash tools/test_ramp39.sh
bash tools/test_ramp39_model.sh
```

第二条会重新编译核心，避免旧 `.class` 导致假通过。日志和测试支持代码均随包。测试入口内部比较实际生产结果，失败抛异常，shell 通过 `set -euo pipefail` 保留失败退出码。

`input-baseline-audit.log` 是修改前运行的反例探针，里面 P02～P06 的通过输出表示“复现旧缺陷”，不是新版预期行为；新版预期由 `Ramp39Validation` 断言。旧 P01 示例把边线 2.3 当作演示数值，不表示默认每个道路都是 0.3 格差值，详见改动记录。

补丁交付前另行执行 `git diff --check` 和在原 ZIP 的干净解压副本上 `git apply --check`，再实际应用补丁并逐文件比较；结果见随交付单独提供的 `SplineRoads-0.39.0-alpha-delivery-verification.json`；该侧车文件包含补丁摘要，因此不写回补丁自身。历史 0.38 文档里的验证结论不继承为本轮完成状态。
