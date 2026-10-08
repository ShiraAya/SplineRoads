# SR435 0.40.22-alpha WIP（2026-10-08）

UTC开始09:06:44，基线GitHub 30f4826。11张新截图已实际读取，反馈优先于旧32项结果。已修改：非下降AUTO整轮上跨优先；新增满足用户半径的平滑Bezier候选；负净空显示实体重叠；护栏/底座整组保留或裁除；外侧封闭移除残余肩带/护栏；端帽0.5m纳入实际封闭；连接器用立交匝道立柱；禁行X警告范围边界白实线；连续可种植段12m阈值、收窄端头、阻挡区低混凝土封底。

核心和模型原回归已运行；显式OVER复合夹具发现平滑候选改变其预定交叉位置，因此新平滑候选仅用于AUTO，之后原复合2239检查通过。新增针对性几何测试及真实Forge/ServerLevel验收待完成。旧测试中固定绿化件数与固定孔宽断言需按新外侧肩带语义更新，不能放松净空与旁车道保护。

本地Java17存在，javac使用jdk.compiler模块启动；本地Gradle下载当前网络不可达，准备GitHub Actions完整构建及真实服务端测试。本次不声称客户端图形或原存档验收。后续入口：Live435Validation、LaneClosureLandscape、RoadStructures、LaneRamps，完成测试后更新报告与交付。

---
# SR434 / 0.40.21-alpha 最终交付（2026-10-07）

本轮 UTC 开始12:04:23。基于上传0.40.20-alpha-rc1；生产修复集中在LaneRampPaths和LaneRamps：补全AUTO四种CSC方向族、先短连接及同车道过渡后回环、准确保留失败原因。版本升至0.40.21-alpha，协议65、存档格式不变。

指定A–D×1–8场景真实Minecraft ServerLevel 32/32通过；固定同一高架东端的弹性同车道落点追加32/32通过。每项独立预检、写入建造、生产净空、Mojang NBT往返、删除恢复。两个真实矩阵都是普通道路；高速和镜像另外做模型验证。普通/高速精确B、固定东端普通、固定西端左行高速四组模型合计128/128。既有核心、复合、Arrival417和Regression433及新增54项方向族回归通过。Java17/Forge47.4.20编译及JAR/reobfJar通过。

高速B→5确定性路线540.361→402.594m，最大坡比13.9093%→10.8684%。详细设置、全部32项表、复现命令与边界在SR_0_40_21_VALIDATION.md；原始日志及路径CSV在validation-results/0.40.21。未运行客户端图形、光影、真实车辆、多人网络或用户原存档测试。额外扩入精确锁在没有上游渐变空间的道路起始端应继续拒绝；默认弹性模式沿同一车道找落点。

本地分支chat/sr-04021-ramp-matrix，基线f678d79；最终提交可从本地git log回读，源码附件包含本轮完整工作。当前连接未找到可访问SR远端，因此未推送远端，已生成完整源码ZIP、独立补丁、JAR和HTML报告。历史检查点保留如下。下一步只需按用户新实机场景反馈继续，不能把服务端测试扩大解释为用户世界验收。

---
# SR434 0.40.21-alpha 第二检查点（UTC 12:35）

已完成：AUTO四种CSC回转方向族；先短连接/直行过渡后回环；错误类型归属。普通/高速精确B、固定东端普通、固定西端左行高速四组128/128模型通过；相关核心、复合、Arrival417和Regression433回归通过。B5高速路线540.361→402.594m，最大坡比13.9093%→10.8684%。

真实Java17 Forge47.4.20 compileJava、compileGameTestJava、jar/reobfJar成功。真实ServerLevel夹具首轮因把端点中心跨度与端帽后网格长度混同而提前退出，已改为验证端点中心500m并按端点投影选择车道点；生产端帽逻辑未改。当前正在新run434b世界执行32项真正预检/建造/保存/删除。此前客户端assets下载超时，服务端以-x downloadAssets运行，未关闭生产校验。

本地分支chat/sr-04021-ramp-matrix；远端当前不可访问，附件检查点保存。最终结果以SR_0_40_21_VALIDATION.md后续更新为准，尚不声明32项实际建造通过。

---
# SR434 / 匝道矩阵检查点（2026-10-07）

本轮 UTC 开始 12:04:23；用户上传 0.40.20-alpha-rc1 为唯一源码基线。
工作分支 chat/sr-04021-ramp-matrix；本地基线 f678d79。当前连接未找到可访问的 SR 远端仓库，先保存附件检查点，不声称远端已同步。

已新增可重复 A–D × 1–8 测试：两条端点中心跨度500m（含501个方块位置）的双向六车道，交点居中，高差8m，普通4m车道/高速5m车道。A为EXTRA、其余TEMPORARY；1/8为EXTRA、2–7为MERGE；每例从干净宿主开始、校验方向/轴线/坡度/净空/封闭车道/保存删除恢复。

基线按行驶方向选择高架两侧出口：普通32/32、高速32/32模型通过；不等于真实建造通过。固定同一高架端点：反向半幅12个现有车道路线失败、4个额外入口因没有上游64m过渡而拒绝。补全AUTO四种圆弧-直线-圆弧方向族；显式左右转规则未变，未关闭保护。修复失败原因误写AUTO键而丢失真实方向诊断。新算法正在验证，未发布结论。

真实Forge构建依赖准备中；新增ServerLevel 32项测试已写，尚未运行。后续入口：RampMatrix434GameTests、RampMatrix434Validation，生产修改 LaneRampPaths/LaneRamps。继续检查全流程建造与回归、打包和报告。

---
# SR433 final verification pending

Full regression found a real disjoint-intake regression: the provisional cut covered the whole lane chain and overlapped a distant valid intake. Real physical ranges are now created only after the candidate exists; slot-specific collision guards remain during route search. This is not a clearance exemption. Arrival417 tests no longer demand fixed64m/40m or blanket predecessor refusal, but keep closure, exactB, adjacent-lane, codec and deletion checks. All78 Arrival417 scenarios/1008 checks passed locally after the repair. Initial real3case Minecraft and Forge passed; final same-fixture run and complete regression are pending on the saved source. No further feature expansion this turn.

---
# SR433 WIP: physical lanes and actual impact bounds

Version0.40.20-alpha-rc1, protocol65. Latest nine screenshots reopen correctness: lane-continuous search across joined roads, contact-derived reservations, distinct below-grade mouths, no departure X, real opening edges, local tunnels and bridge piers. Exact source is saved before full regression and real Forge/ServerLevel tests. No original-world acceptance or completed-release claim until verification finishes. Prior perf2 collision hot paths and cancellation remain; no fixed interactive cutoff.

---
# 当前：SR432 / 0.40.19-alpha-perf2 双顺序性能验收通过

仅工作分支SR-0.40.15。受测生产代码、JAR与精确源码归档均对应2f3f6ef48bdebe7a296804db459ed05b448969be，协议64。之后只更新日志读取器、CI和本文档，不改受测生产代码。

用户要求路线规划和完整预览/建造都提速，不以固定时限结束代替优化。本轮默认关闭低命中的全Mesh值栅格缓存；优化水平矩形路面与设施的等分辨率碰撞生成、固定数字比较器及高度剪枝；净空查询采用等价ID去重及线程私有裁切数组。保留精确判断、坡比/方向/净空/其他车道/方块实体/所有者保护、取消和过期结果拒绝，没有恢复固定短时限。

## 本轮结论

真实Java17 Forge与完整新旧回归通过：Actions37590187644，regression112689592399。新增26397栅格/构件/合并、55955净空接触/顺序精确比较通过。

实际Minecraft对照两轮分别旧->新、新->旧，同一轮同一runner和相同夹具，每版本每场景4次完整路线/只读预览/建造，第0次单列、后3次中位数。两轮碰撞单元数量及完整装配形状指纹一致，ADD真实3->4/NBT保存加载/删除恢复、高速临时封闭预约和删除恢复均通过。

- 第一轮ADD：路线20.969301->16.740174ms，预检1100.910365->663.011247ms，建造1163.014524->699.985165ms，总计2278.632087->1456.342042ms。
- 第一轮TEMPORARY/MERGE高速：路线1065.222363->800.708057ms，预检4073.164328->1815.923954ms，建造3654.308692->1493.662864ms，总计8792.695383->4110.294875ms。
- 反向顺序ADD总计2253.648851->1237.931841ms；高速总计8220.852581->4408.809650ms。路线各减少28.4%与5.4%；不要只挑第一轮较大的路线收益当作稳定常数。

原始两轮比较脚本被stdout/stderr日志交错打断，误认为少一条BENCH记录；所有数字仍在原日志中。只修读取器、增加算术核验，没有改日志/测量/道路/门槛。最终Actions37592018840第二次尝试accept112696870660成功，用两份不可变原始产物重新验证，所有阶段比率<0.98、总计<0.90。第一次尝试只因反向测试尚未结束退出。完整情况见docs/checkpoints/FINAL_VERIFICATION_432.md。

JAR SHA256：843e9cec3652f0ea420aafa1cf45cac414c989cbb8908700e984c9aee81f34f9。
源码ZIP SHA256：4f39632430f8f5d9b1ede3388fa522bea099a102566afb6fcf8338ac599ecfb7。
622个src/main文件与本地生产修改逐字节一致，Manifest/mods.toml版本一致。构建产物11469030575、第一轮11469345104、第二轮11469172327、最终验收11468373827均已取回并校验。

## 范围与下一步

这是开发侧实际服务端功能和性能验收，不是用户原存档、真实客户端GUI/GPU、网络或车辆验收。最终世界写入仍在主线程，可能短暂停顿。完整流程计时不包括测试准备、JVM启动、统计断言或删除；所有冷/热原始测量保留。

本轮可以交付perf2安装候选。退出游戏备份存档，只安装一份SplineRoads，联机两端统一版本，重新选A/B预览。协议仍64，不需要全存档重建才能获得计算优化。若后续有新原场景性能反馈，以具体路线/预检/建造分阶段定位，不恢复短时限或降低保护。

全部生产代码和测试已保存；本轮结束时只完成已启动验证的结果归档，无后台修改承诺。

---
## 历史：SR431 未通过，perf1仍不推荐

SR431基于e3ac9269、归档59988961，路线有所加速但完整流程变慢。单次对照ADD总计3681.731725->4164.577211ms，TEMPORARY/MERGE总计10408.128636->13817.815908ms。低命中全值缓存及碰撞合并路径需要消融，不能用编译绿灯当作性能通过。

该失败阶段的完整数据、源码产物、历史失败和边界保留于docs/checkpoints/FINAL_VERIFICATION_431.md；早期未结束时的记录为SR431-SAVED.md。精确perf1源码ZIP SHA256 1e43f6454b7d26342f9d70ab7bb1e2480bc8e42a2781cdeaf287efe17d21ab8c；实验JAR SHA256 077fb6fd3707b75655b6f9cd338e689bef13c1bc1915514362d497c5503ea07a。

上一交付0.40.18-alpha为32cee0fa，协议64。中央分隔带节点基准、四档滑块、立交汇流箭头和封闭区无显式框架空指针修复保留。用户反馈已证明固定短时限不能代替匝道优化；旧实机测试不作为后来问题结案证据。更早记录见FINAL_VERIFICATION_430.md、FINAL_GAME_TEST_430.md、FINAL_VERIFICATION_429.md。
