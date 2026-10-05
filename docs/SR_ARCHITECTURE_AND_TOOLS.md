# SR 架构与道具核对

审阅基线：hotfix2，`19ac5be`。本表根据注册代码、各道具入口、客户端界面、服务器命令及建造事务核对；用于后续修改前定位已有能力，不能当作所有功能的测试通过证明。

## 现有道具

| 注册名 | 实际功能与主要入口 |
| --- | --- |
| road_node | 放置普通真实端点；NodeEntity 保存偏移、朝向、坡度和所有者；供普通道路、路口和生成器选取 |
| endpoint_creator | 红色附属点，放置／编辑道路内部定位点（本轮补修以放置原点显示相对坐标，删除撤销控制贡献）；平滑曲线可 XYZ 调形，其他模式仅高度；立交内仅标线分段；不是匝道入口 |
| lane_point_tool | 蓝色车道点，右键创建或编辑；固定车道中轴线；手动点可换车道／删除，自动尽头点只读 |
| road_connector | 两真实端点接路，右键路面编辑，道路端点 Shift＋右键调朝向／坡度；实景预览、草稿恢复、道路附属设置；Shift＋右键空气清除 |
| ramp_connector | A 车道点汇出、B 车道点或路口中心汇入；独立单向一车道；Shift＋右键清除；本轮补齐实景预览恢复与已建匝道编辑 |
| junction_connector | 中心＋3–6 接入口生成普通路口；右键普通路口、环岛和 Y 字路口编辑；道路连接器还可自动形成普通路口 |
| roundabout_generator | 空闲端点作圆心，单／双车道、半径 10–48 格环岛；生成八方向接路点；已有环岛交给公共路口界面 |
| interchange_connector_3 | A/B 主路＋C 支路，三向立交；只编辑匹配的三向描述 |
| interchange_connector | A/B 与 C/D 两贯通主路，四向立交 |
| interchange_connector_5 | A/B、C/D、E，五向立交；当前基线已开放，不是占位道具 |
| interchange_connector_6 | A/B、C/D、E/F，六向立交；当前基线已开放 |
| frontage_generator | 复用 InterchangeTool，A/B 主路，生成同向辅路及连接；由 CorridorScreen／Corridors 处理 |
| layered_connector | 复用 InterchangeTool，A/B 第一层、C/D 第二层，平行双层同侧出入口组合 |
| gantry_editor | 点击龙门架或其下道路，按站位选最近一架；保存单架偏移、设施配置，签名校验 |
| lane_line_editor | 点击道路定位单根标线和附属点划分的区间；修改样式、宽度及箭头显示，不改整路车道结构 |
| y_junction_editor | A 双向、B 出向、C 入向，三点生成可通行 Y 字；普通路口编辑器可再编辑该组合 |
| road_remover | 256 格射线选路；按独立道路／路口／组合走对应删除界面；引用依赖需确认 |

以上 17 个入口在创造物品栏中。`sign_editor`、`road_pole` 当前只注册为普通 Item 且未加入物品栏；虽然保留 RoadSignTool 等旧源码，不能据类名声称它们仍是可用编辑工具。四类点身份必须保持独立。

## 数据和建造流程

1. `SplineRoads` 注册；各 `*Tool` 验证选取／所有者，发 `RoadNetwork.open`；`ClientRoads` 路由界面。
2. 界面生成草稿和 Action；`RoadNetwork.perform` 校验手持工具、权限和限速，分发服务器命令。
3. `RoadRecord` 保存身份、两端、Settings、已采样 Alignment、结构、组合归属和路口引用。车道点／连接引用在 `LanePoints`、`LanePointCodec`；附属点与分段标线是另一套 metadata。
4. `RoadData.replaceBatch` 统一事务：断面衔接、附属点迁移、车道点／依赖匝道联动、结构与净空、清障／碰撞／方块写入、保存与广播。`LaneTopology.reconcile` 不能被直接写 index 的方式绕过。
5. `RoadIndex.Built` 持有真实 Mesh、栅格、碰撞缓存；空间索引、渲染细分和元数据同步职责不同。无引用车道点只改元数据，引用中的点修改仍进入完整事务。
6. `StructurePlanner` 调用 `RoadStructures`、`RoadSupports`、`RoadStreetscape`，处理桥墩、路缘、护栏、路灯、人行道及避让。`SmartSidewalks` 管理实际方块／边缘填充。
7. `RoadSurface` 处理铺装和重叠所有权、边线开口、标线；`RoadJunction` 提供现有导流与箭头几何；`RoadRenderMesh` 仅做视觉简化，不能替代真实碰撞。
8. `RoadRenderer` 后台生成网格、按区域上传与分级显示；世界预览走 `ClientRoads.preview/nodePreviews`。新道具应复用这条预览，不另建脱离世界的预览状态。
9. 网络通过 RoadWire／RoadInbox 分片、排队；客户端缺失基底时回退完整快照。保存与世界加载由 RoadData、RoadRecord 和各 Codec 负责。

## 原有可复用能力和本轮缺口

- 道路与立交界面的“实景预览”会收起界面，保留草稿以继续操作；匝道界面只赋值 Mesh，既不收起，也在关闭时销毁预览，未完整接入。
- 普通道路编辑器原本通过真实 NodeEntity 取得两端；车道点匝道保存的是引用端口，不能假定对应方块存在 NodeEntity。
- 旧样式匝道通过 `Style.ramp()` 区分渲染优先级；车道点匝道是普通／高速单行道，须依据真实 Link 判断主从，不可按随机 UUID 决定是否盖掉主路标线。
- 自动立交已存在渐宽加减速车道和并行段。车道点连接器旧“扩出”只有横向曲线，缺少完整的并行车道；应复用通用采样路面、碰撞、接头清障和绘线能力。
- 用户本轮放宽 B 的精确纵向位置，优先于 V2 的旧精确接入位置要求；仍保留 B 的真实引用、同一车道与方向，并存储实际偏移，不能偷偷换车道。

## 后续修改前检查

先看注册和道具入口，再看公共界面与网络路由，再查实际建造、保存、引用与渲染。修改某工具必须同时核对：创建、预览返回、再次编辑、失败回滚、删除依赖、退出重进；不能只检查首次建造。
