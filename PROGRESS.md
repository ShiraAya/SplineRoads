# SR15 第二批修复已保存：方向独立车道数与实际端点续接

2026-10-06。本轮从2e90ae6继续，开始UTC14:26:43。工作分支仅SR-0.40.15；main、SR-0.40.14、chat/sr-0402不修改。

候选0.40.16-alpha-stage2，协议61。最终受测代码c62e440a8af4633cad8089d5dabacdf2fc4b5ff7。GitHub Actions 37483428488成功；verify作业112337331178，执行bash tools/check_sr427.sh。实际Java17 Forge编译、compileGameTestJava、jar/reobfJar及完整新旧回归通过。没有运行GameTest服务端或Minecraft客户端。

本轮SR15-09/10/11：RoadLanes显式A→B/B→A每方向1–4数量、RoadProfile/RoadTransitions非对称断面及NBT；RoadLaneConfigScreen滑块；RoadEndpointSections快照当前永久合并后的物理端口；RoadTool/RoadData/AutoJunctions使用实际中心、宽度和方向，新续接不复制源路合并归属、不重复裁切原路。RaisedRoadProfile非对称分隔带变换与过渡标线身份已修正。旧记录无DirectionalLanes字段仍按旧预设读取。

新核心Directional427：180案例/4114检查；记录/断面模型Directional427Model：96案例/961检查，均在最终CI中PASS。原Transition402的966合法/544拒绝/11789409检查保留且PASS。首次运行37481884813曾在该回归失败、未进入Forge，已修正生产代码，没有删掉拒绝断言。

已取回源码/日志与JAR，核对Actions归档SHA256、源码ZIP提交注释、JAR版本及新类；本地1821个跟踪文件对比仅PROGRESS.md存在预期工作流生成差别，生产代码一致。交付校验值及边界见docs/checkpoints/FINAL_VERIFICATION_427.md。本提交仅文档，不改变受测代码。

## 还未完成与下一轮入口

SR15-03侧壁缝隙、SR15-07新匝道影响旧绿化、SR15-08整车道分离多余导线仍待修。后续先读docs/feedback/SR-0.40.15-2026-10-06.md与FINAL_VERIFICATION_427.md，并核验分支头；不能将原14项全部标为结案。

实时断面仍需补已有续接后再修改/移除原合并的依赖传播与接缝重建；本轮只验证创建/删除续接不破坏原合并，不代表删除源合并也已自动重建整网。专项Y字/多向立交生成器保留旧模板接口，尚未整体改为非对称输入。A/B是道路自身方向，单向没有新增逆向单行或自动交换选点。

未运行用户原世界、Minecraft/GPU、实际鼠标滑块、车辆、真实服务器/多人事务；SR15-09/10/11状态为代码实现及核心/记录/编译验证完成，待实机闭环，而非原截图已全部复现。旧stage1设施与纵坡修复保留。P2 terrain/光影/多线程不扩展。

本轮源码和进度均已远端保存；结束后不承诺继续后台修改。历史进度保留在c62e440、2e90ae6及先前检查点提交。
