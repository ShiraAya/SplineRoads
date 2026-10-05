# 车道点与匝道取消操作修复（hotfix1）

本文件保留 hotfix1 的历史变更和验证。当前交互、右转、界面修复及协议 50 说明见 `LANE_INTERACTION_HOTFIX.md`。

本补丁基于上一版 lane-ramp-v2（Git `3390bfa`）。继续以 `SR_Codex_LanePoint_Ramp_Constraints_v2`、`SR_Codex_Endpoint_Constraints_v2` 为相应功能的主约束，并补充本轮明确要求：车道点必须吸附所属车道中轴线；匝道连接器使用 Shift＋右键清除选点。

## 修复结果

- 修复旧版已建道路更新、新建车道点时的“断面与车道点未能稳定”错误。
- 点击机动车道时，蓝色点自动吸附该车道的中轴线；不提供附属点式的任意 XYZ 编辑。切换车道保留沿路位置，允许切换反向车道。
- 旧手动点加载时校正到对应车道中轴线，保留点 ID、车道编号、来源和匝道引用；自动尽头点由真实道路端点生成。仍使用蓝色车道点、红色附属点及既有专属工具贴图。
- 匝道连接器 Shift＋右键清空 A、B、预览凭据和选择维度，发送客户端重置以清除预览；对空气、方块或点均有效。重复右键 A 保留选择并提示另选 B，不再取消。
- 取消后的预览凭据不能用于建造；再次普通右键可重新选择 A。

## 根因与处理

旧实现把带有横向偏移的车道点坐标反投影到道路中线，用这个投影再次计算车道中心。曲线的插值横向基向量与采样线段不完全垂直，反投影会改变沿路位置。已复现最大单次偏移 `0.01943854553513143` 格，超过拓扑稳定性检查的 `1e-5` 阈值。曲线自动点每轮又从道路端点重新生成，因而无法收敛。稳定性检查覆盖全世界道路，所以一条旧曲线也可能阻塞另一条直路的编辑或放点。

现在每个点存储道路中线上的纵向锚点 `Anchor`，显示坐标由锚点与车道编号派生；后续更新不再使用横向偏移后的显示坐标作为纵向输入。投影包含高度，避免上下交叠路段误吸附。已规范化的点保持原值，防止连续保存产生浮点抖动。拓扑稳定性检查及其阈值保留。

## 兼容与安装

- Minecraft 1.20.1 / Forge 47.4.20；模组版本仍为 `0.38.0-alpha`。
- 道路数据版本从 31 更新为 **32**。读取旧版本数据时补齐锚点，迁移结果随存档保存；新存档不应交给旧 JAR 读取。
- 网络协议从 48 更新为 **49**。多人游戏服务端与客户端均须换用本次 JAR。
- 完整源码包已包含上一轮改动和本轮修复。`LANE_RAMP_V2.patch` 是上一轮的历史补丁；本轮增量为 `LANE_POINT_HOTFIX.patch`，应用基线是 `3390bfa`。

## 验证

验证记录与截图见 `validation-evidence/lane-point-hotfix1/`。本轮未收到用户实际世界存档；旧路兼容测试使用重建的版本 30/31 直线、曲线及偏离中轴线的手动点数据。

- `validateLanePointSnap`：742 项检查通过，含旧漂移复现、曲线与直线所有车道、100 次重复吸附、切换反向车道、上下交叠路径及旧点迁移。
- `validateLaneRampV2`：2595 项检查通过。
- `validateEndpointV2`：通过。
- `runGameTestServer`：15 项世界测试全部通过，包含旧曲线对全世界更新的影响、旧路更新与放点、版本 30/31 迁移、NBT 往返和既有端点／匝道行为。
- 实际客户端 `lane-hotfix`、`lane-direct`、`lane-point-ui`：全部通过。使用实际玩家的道具与命令入口更新旧直路、旧曲线，偏心点击放点，重复点击不重复生成；生成真实匝道预览后分别对空气、方块 Shift＋右键清除，旧凭据建造被拒绝且道路数据不变，可重新选 A；另验证正常匝道建造和车道点界面。批处理脚本模拟五个游戏刻的输入间隔以遵守现有指令限速。
- 已检查实际客户端截图：蓝色点位于车道中轴线，界面明确提示固定吸附并保留换车道／删除入口。
- 完全退出并重新启动客户端、打开同一世界：`lane-hotfix-reload` 通过。所有道路 ID、点 ID／来源／车道／中轴线位置／锚点、匝道引用一致，旧道路碰撞正常重建。

复现入口：

```sh
./gradlew validateLanePointSnap validateLaneRampV2 validateEndpointV2
./gradlew runGameTestServer -ProadTestNamespaces=splineroads_lane_v2,splineroads_endpoint_v2
./gradlew runClient -ProadVisualTest -ProadVisualCases=lane-hotfix,lane-direct,lane-point-ui
./gradlew runClient -ProadVisualTest -ProadVisualCases=lane-hotfix-reload -ProadVisualReload=<上一条命令生成的世界目录名>
```

客户端复测应沿用相同运行目录，确保 `visual038/lane-point-ui-roads.nbt` 与世界对应。测试源码不会打入发行 JAR。
