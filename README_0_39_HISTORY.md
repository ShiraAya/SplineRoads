# Spline Roads 0.39.0-alpha — 自由匝道改造版

Minecraft Java 1.20.1 · Forge 47.4.20 · Java 17。直接修改用户提供的 `SplineRoads-0.38.0-alpha-ramp-workflow-hotfix3-source.zip`，没有切换其他分支或恢复旧匝道连接器。

**这是已修改的完整源码，不是已完成 Minecraft 实机验收的发行包。本交付不包含编译好的模组 JAR。** 本环境无法解析 Gradle 下载域名，完整 Forge 编译未完成；核心测试及带测试适配器的服务端规划／界面状态测试已运行，准确范围见验证记录。

## 本版入口

- [实际改动、支持范围及限制](RAMP_0_39_CHANGELOG.md)
- [执行过的验证、原始日志及未验证项](RAMP_0_39_VALIDATION.md)
- [操作、编译与组合图测试说明](RAMP_0_39_BUILD_AND_TEST.md)
- [逐文件修改清单](RAMP_0_39_CHANGED_FILES.txt)

车道点与匝道连接器仍采用先选 A 汇出、再选 B 汇入；附属端点不参与匝道选点。现有实景预览和两类匝道编辑入口保留。

### 新连接设置

| 设置 | 可选项 |
| --- | --- |
| 汇出 | 保留原车道分流、整车道分离、额外扩出 |
| 汇入 | 并入现有车道、补入车道空位、额外扩入 |
| 路径 | 自动、右转、定向左转、左转回环、直接连接 |
| 高程 | 自动避让、上跨既有道路、下穿既有道路、保持原高程 |
| B 落点 | 同车道弹性落点、精确锁定 B |

**整车道分离目前支持单向路的一侧边缘车道；补入空位要求同一实际路段、同一车道槽位已有上游分离。** 这是本次实际支持边界，不能视为任意内部车道挖出、双向道路全部组合或完整收费广场已经实现。单车道一分二使用“保留原车道分流”。

## 编译与测试

首次下载依赖需要联网，使用 JDK 17。在 Windows PowerShell：

```powershell
.\gradlew.bat compileJava compileGameTestJava validateRamp39 validateLaneRampWorkflow jar
```

在 Linux/macOS 的依赖完备环境：

```sh
bash ./gradlew compileJava compileGameTestJava validateRamp39 validateLaneRampWorkflow jar
```

成功后由 ForgeGradle 重混淆，模组输出到 `build/libs/`。不能把随包 `gradle-wrapper.jar` 当作模组安装。

离线核心／规划测试使用 Bash 4+ 和 JDK 17+：

```sh
bash tools/test_ramp39.sh
bash tools/test_ramp39_model.sh
```

第二条会重新编译核心，避免误用上次编译的类。测试支持代码在 `tools/`，不进入正式 sourceSet 或模组 JAR，不能复制进 `src/main`。

**新增实际 Minecraft 组合场景尚未执行：**

```powershell
.\gradlew.bat runClient -ProadVisualTest -ProadVisualCases=ramp39-compound -ProadRunDir=run039-visual
```

只在隔离测试目录运行。它会真实建路、保存并截图，沿用现有 `visual038` 输出目录命名，不是已附带的游戏录像或验证成功截图。

## 存档与多人游戏

先备份整个世界，再在副本测试。存档格式升至 **34**，网络协议升至 **52**，客户端和服务端必须同时使用同一版本。新代码保留旧字段读取与安全默认值；实际旧存档与新存档的 Minecraft 二进制往返尚未实机验证。新版保存后不要用旧版读取。

## 历史资料与许可证

`README_0_38_HISTORY.md`、`RELEASE_0_38.md`、端点 V2、hotfix1/2/3 等文件为输入包的历史说明；其中的完成状态和旧版本测试不能当作 0.39 的新验证。原有无关功能、资源和历史测试保留。

第三方资源来源及许可见 `THIRD_PARTY_NOTICES.md` 与 `THIRD_PARTY_LICENSES/`。
