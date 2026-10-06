# SR430 最终真实Minecraft验证补记：两项通过

本补记更新FINAL_VERIFICATION_430.md中初次交接时的“最终minecraft作业运行中”状态。没有再次修改代码、测试夹具或安装包。

最终代码、JAR、source ZIP仍为32cee0fae8a03726bf3fd94880111ef1a07e6925，0.40.18-alpha，协议64。Actions37511903071的build作业112434904958和minecraft作业112434904263均已完成且SUCCESS。

## 真实服务端结果

实际Minecraft Forge1.20.1 GameTest运行两项，日志显示“2 GAME TESTS COMPLETE”和“All 2 required tests passed :)”，不是只编译、空跑或模型替代。

| 用例 | 路线规划 | 实际世界建造 | 检查结果 |
| --- | --- | --- | --- |
| TEMPORARY/MERGE：带设施的双向6车道高速，保留车道分离后汇入 | 3432.999455毫秒 | 4520.943741毫秒 | 真正创建匝道记录、碰撞单元和源道路封闭预约，删除后恢复预约；BUILD430 TEMPORARY_DELETE_PASS。 |
| ADD：最外侧永久补入，宿主3变4 | 49.762899毫秒 | 1253.919726毫秒 | 真正创建匝道/碰撞，主路变4条，经Mojang NBT保存加载仍为4，删除恢复3；BUILD430 ADD_SAVE_DELETE_PASS。 |

TEMPORARY测试采用与上一失败运行相同的源/目标道路、端点和选道设置。修复封闭区端部构件的空框架裁切后，之前失败的实际建造路径通过。没有通过移走障碍、换道路形状或删除保护断言换取成功。

规划计时调用实际生产几何；建造计时调用LaneRamps.build及真实ServerLevel。测试中的地面准备、远处区块生成、启动和退出耗时不计入这两项计时。该GameTest没有打开客户端GUI，不验证实际网络异步预览的客户端FPS；后台调度器线程/取消/超时另外由实际类加显式服务器适配器的12项测试覆盖。

不能据此断言用户原存档91.3秒卡住的唯一原因已经复现，也不能把这些开发用例与截图直接计算加速倍数。最终世界提交仍在主线程，可能短暂停顿；本版重点消除主线程整轮匝道路线搜索和无期限等待，并修复已复现的实际建造空指针。

## 证据及包一致性

真实服务端产物11435688917，已下载为SR430-final-minecraft.zip并核对外层SHA256：9d682c4cd564632e7abe70bbd7086a501a9b95ebd13995d73aacb15dc3728051。

内部build/repro430/server.log有两项规划/建造计时与PASS标记，run430/logs有完整服务端日志及自动线程采样。首次生成server.properties时的NoSuchFileException为服务器随后创建配置的启动记录，不是本次GameTest失败；以真实测试断言和最终BUILD SUCCESSFUL为准。

安装产物11435518388对应同一32cee0fa提交。JAR SHA256 d7a31a08f45f187bbc982e3846ddf94a0e7a8064074ddd23fba839eebc0c284f；完整源码ZIP SHA256 6d19360ac3842817c65b01ec70bb0ca3d6037dbda073874adfc80b9f0e133c41。版本、协议64、621个src/main文件逐字节一致性已检查。

交接前已保存全部生产代码；之后仅收取已启动验证的结果和归档，没有继续扩展功能。历史失败日志保留在验证记录中，用户原场景/客户端视觉效果待实际替换此包验证。
