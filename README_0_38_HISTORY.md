> 端点 V2 修复版：更新内容见 [ENDPOINT_V2_CHANGELOG.md](ENDPOINT_V2_CHANGELOG.md)，逐项验证与限制见 [ENDPOINT_V2_ACCEPTANCE.md](ENDPOINT_V2_ACCEPTANCE.md)。

本源码包含端点 V2 与车道点／匝道 V2 更新，以及 [旧路更新／中轴线吸附修复](LANE_POINT_HOTFIX.md)、[车道点响应／右转／俯视界面修复（hotfix2）](LANE_INTERACTION_HOTFIX.md)、[匝道预览／编辑／真实扩出修复（hotfix3）](RAMP_WORKFLOW_HOTFIX.md)。使用方法见 [车道点／匝道更新说明](LANE_RAMP_V2_CHANGELOG.md)，逐项验证及剩余验证范围见 [验收记录](LANE_RAMP_V2_ACCEPTANCE.md)。协议 51；客户端与服务端应安装同一份新版 JAR。

# Spline Roads 0.38.0-alpha

Minecraft Java 1.20.1 · Forge 47.4.20 · Java 17。

沿用 SR 0.37 基线，不合并搁置的 0.36。当前匝道连接器已按车道点／匝道 V2 重做，支持真实车道点选取、预览与建造；Shift＋右键清除选点。已有道路仍可读取，立交生成器使用的共享道路几何保留。

## 本版修复

- 路口接入端读取自己的单双向、车道和非机动车道断面；已在路口处截短的道路不再把逻辑中心当作普通续接端点。
- 删除支路不再因建造时的车道兼容检查而被阻止；编辑本地道路时，保留远端路口裁切，跳过未改变路面的重复净空校验。
- 人行道高差连接改为连续铺装过渡，盲道贴合铺装；有、无人行道相接的转角补齐。
- 汇向同一出口的路口导向虚线提前汇为公共线段；斑马线横跨非机动车道和分隔带。
- 单臂路灯的弧形支撑改朝车行道外侧。
- 人行道上方与道路一起清障；清除的障碍不再备份或在删路后恢复。道路边缘仍可见的地形填充继续保留。
- 在已有高架下新建道路时，同步更新影响通行的桥墩。桥墩基础算法沿用 0.37.2，本版仅修复更新依赖。
- 修正远离坐标原点时路口三角面之间多余内壁造成的放射状黑缝。

## 安装与旧存档

将 `splineroads-0.38.0-alpha-ramp-workflow-hotfix3.jar` 放入 `mods`，移除旧 SR JAR；多人游戏客户端和服务端同时更新。

旧路口和已保存设施不会在加载时全部重建。对需要应用新铺装、盲道、斑马线或路灯布局的已有道路／路口，打开相应编辑器并保存一次。匝道连接器右键依次选择 A、B，界面中预览并建造；Shift＋右键清除选点与预览。右键匝道路面可用匝道连接器调整线路与扩出，或用道路连接器调整路宽、路面和附属设置；道路删除器可移除独立匝道。实景预览会收起界面，右键空气返回，关闭界面保留预览。

当前存档格式 33；网络协议 51。兼容读取旧道路，版本 30／31 车道点按中轴线锚点迁移；新版保存后不要交给旧 JAR 读取。

## 编译与验证

需要 JDK 17。使用随包 Gradle 8.8 Wrapper：

```sh
./gradlew jar
./gradlew validateRevision38
```

`jar` 自动执行 Forge 重混淆，输出到 `build/libs`。

实际图形客户端重现场景：

```sh
./gradlew runClient -ProadVisualTest -ProadRunDir=run038-visual
```

可用 `-ProadVisualCases=editor` 单独检查普通道路编辑界面。

此任务在真实 Forge 集成世界中建造、编辑、删除道路并保存截图，结果位于运行目录的 `visual038`。测试仅在显式指定 `roadVisualTest` 时启用，测试源码不进入安装 JAR。验证范围和说明见 `RELEASE_0_38.md`。

第三方资源来源与许可证见 `THIRD_PARTY_NOTICES.md` 和 `THIRD_PARTY_LICENSES/`。
