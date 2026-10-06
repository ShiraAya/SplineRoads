# SR-0.40.15：首批实际修复已保存并通过完整CI

日期：2026-10-06。本轮从13:32 UTC开始，完成首批代码及检查点后暂停，等待下一轮继续。不是14项全部完成。

## 已核实的源码与产物

- 工作分支：SR-0.40.15；基线376ceae / 0.40.15-alpha-p1.1。
- 当前候选版本：0.40.16-alpha-stage1。
- 受测代码提交：f196c42d2db27ad98d93ed924d02179dab8e2d13。
- 完整GitHub Actions：37476740057；verify作业112314068550，SUCCESS。入口bash tools/check_sr426.sh。真实Java17 Forge编译及所有串联旧/新回归成功。
- JAR SHA256：50fc1348a171adf91d3f2925545e5d8fb777b4109ef192d12c63c969c2e16ea2。
- 源码ZIP SHA256：c8f99e60b16dcd38e82c441d5a674d8f6d6c34ca402ba5930c58bb4d4e26462e。
- 已下载并核对Actions归档摘要、源码Git提交、1804个本地跟踪文件逐字节一致；唯一跟踪差别PROGRESS.md来自工作流写入的新检查点，生产代码无差别。

## 当前进度

已实现待实机验收：01失败详情及定位、02绿化端部收口、04整体纵坡目标、05孔洞端部护栏、06前方叉号、12索引原始车道参照保留、13断面后点位重对齐、14当前外侧身份。10当前站位分方向计数已加入但未贯通所有续接。实际RoadIndex丢失reference在旧代码上复现；新测试通过。第一阶段完整CI发现封闭邻道被越过的回归，现已修正生产保护，未删除原拒绝测试。

仍待修复：03侧墙小缝隙；07新匝道破坏既有绿化专项；08DETACH多余合流导线；09普通道路续接实际匹配（当前仅正确区分高程差与横向偏移）；10端点完整实时断面接入；11每方向1–4滑块与2+1持久数据模型。没有把这些项目宣称已完成。

详细状态：docs/checkpoints/SR15-stage2-426.md；本轮最终证据：docs/checkpoints/FINAL_VERIFICATION_426.md。原问题及11图索引：docs/feedback/SR-0.40.15-2026-10-06.md。

## 下一轮直接继续的入口

优先实现每方向车道数量/端点实际断面的一致模型。重点RoadData.hint、endpointSettings、endpointSection、jointSection、normalizeTransitions。NodeEntity原轴心与合并后物理端点可能横向不同；续接要对齐真实端口，但不能将已经收窄的宽度再次输入原有Cut造成二次收窄。持久槽位ID不改，显示序号/当前外侧独立计算。RoadProfile.Catalog、Layout、RoadTransitions.Port、Options/codec及RoadScreen必须共同支持高速/普通→单向/双向→每方向1–4；不能只改标签伪装2+1。

之后排查03/07/08：RoadStructures侧墙端面；StructurePlanner与LaneClosureLandscape既有结构裁切；RoadJunction.mainGuides/terminalMarkings是否错误将DETACH当辅助合流。上述为待验证方向，不是已证实根因。

额外本地探针tools/check_former_outer426.sh验证4种单/双向和左右行驶组合：先外侧合并，再原中间车道DETACH→同宿主FLOW，删除后保留早先合并。该探针不在上述CI链内，记录与源码单独保存。

无用户原存档、Minecraft客户端/GPU/车辆/真实多人验收。源码及测试已保存；先备份存档使用测试候选，不强制读档全量重建。网络协议仍60。本轮未改其他分支。terrain、光影、多线程不自动扩展。历史检查点见376ceae、76e4c9fe、f196c42d的PROGRESS.md。
