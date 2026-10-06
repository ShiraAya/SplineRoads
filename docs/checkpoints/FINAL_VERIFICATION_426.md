# SR15 首批修复：最终验证与交付记录

2026-10-06。测试候选0.40.16-alpha-stage1，分支SR-0.40.15，受测代码f196c42d2db27ad98d93ed924d02179dab8e2d13。本文件及附加探针的提交不改变受测生产代码。

## 构建及一致性

GitHub Actions运行37476740057的preserve和verify均SUCCESS；verify作业112314068550。入口bash tools/check_sr426.sh串联完整旧回归、实际Forge编译、新索引和新几何测试。forge.txt记录BUILD SUCCESSFUL in 2m 1s，Java17编译和重混淆生成实际JAR。

已下载源码/日志和JAR产物，核对归档SHA256、源码ZIP注释f196c42d、JAR Manifest的Implementation-Version及新类LaneClosureWarnings、LaneRampScreen$FailureDetails、LaneSections$Live、RoadIndex$Built。源码归档与本地1804个跟踪文件逐字节一致；唯一预期差别PROGRESS.md由工作流更新，生产代码无差别。

源码/日志产物ID11419428393，归档SHA256：2b0881ee480b5d03f2f37aa57a1aa6703e3edf9c42e146a8c52d19c52870d741。
JAR产物ID11419488366，归档SHA256：3393a92538bb0187f823848d6585f5acb107c71147f76e11d9db5a0b702c3e7d。

## 主要测试

| 验证 | 场景/检查 | 结果与边界 |
|---|---|---|
| LiveSection425 | 20 / 244 | 实际RoadIndex、RoadRecord与核心；NBT/BlockPos适配器。旧Index第一例reference丢失失败，新代码PASS。 |
| SR15Geometry426 | 13 / 5491 | 绿化端部、暴露横向护栏、三组X警告方向/删除、无多余平台的整体纵坡、诊断定位，PASS。 |
| Ramp39模型/复合/GUI | 657 / 2239 / 20检查 | 原规划器与测试适配器；新失败详情打开/返回及禁止错误提交，PASS。 |
| Q2Close422模型 | 24 / 402 | 含原有封闭邻道不可越过的拒绝检查，PASS。 |
| Live423模型 | 32 / 28020 | 实际规划、车道关系、生成/删除模型，PASS。 |
| Rail419模型 | 12 / 4922 | 实体护栏规划与删除检查，PASS。 |
| P1Review424 | 607 / 3567014 | 原纵坡/预算/绿化核心检查及新端部收口断言，PASS。 |

完整旧测试不止上表。每项结果是该测试自身输出，不应相加当作独立场景数量；核心/适配器检查不是Minecraft实机。

额外本地探针FormerOuter426Probe：4种单向/双向与左右行驶组合，先外侧合并后将原中间车道DETACH接同宿主FLOW，再删除该连接，检查早先合并仍在。4例PASS；源码与复现脚本已另存，不宣称该探针属于上述CI。跨侧固定接头仍可能被真实净空保护拒绝，不等于任意几何布局都可分离成功。

## 本轮已实现与未实现

已实现待实机：SR15-01错误详情/位置；02绿化端部横向底座；04可行包络内整体纵坡目标；05孔洞端部护栏；06上游X警告；12实际Index参考几何丢失修复；13最终断面后重对齐点位；14当前外侧身份与顺序操作。

10仅完成当前位置分方向计数基础，尚未贯通普通端点续接。09只修正原先把三维距离称为高程差的误导提示，实际续接还未完成。03侧墙缝隙、07旧绿化受新匝道影响、08多余导线、11每方向1–4滑块与非对称配置仍待修。不能把14项称为全部修好。

第一阶段运行37473665897曾真实Forge编译通过但完整回归失败于closed receiver accepted。已修正生产receiver保护，原拒绝断言保留。随后一次stage2保存步骤因既有CRLF行尾被git视为尾空白终止；仅设置cr-at-eol保留原行尾，没有规避功能测试。本文件记录的是后来真正通过的37476740057。

## 交付文件

splineroads-0.40.16-alpha-stage1.jar
SHA256：50fc1348a171adf91d3f2925545e5d8fb777b4109ef192d12c63c969c2e16ea2

SplineRoads-0.40.16-alpha-stage1-source.zip
SHA256：c8f99e60b16dcd38e82c441d5a674d8f6d6c34ca402ba5930c58bb4d4e26462e

先备份存档，仅保留一个SplineRoads JAR，联机两端一同更新；协议仍60。旧连接不会强制全量重建，在副本重新编辑/生成检查新派生设施。未运行原世界、Minecraft客户端/GPU、模组车辆或真实多人事务。截图坐标不是道路A/B导出；没有声称原11图均已复现或验收。下一阶段从本分支和PROGRESS.md继续。
