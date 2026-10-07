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
