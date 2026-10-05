# SR 0.40.9-alpha — 已编译的匝道阶段检查点

这是checkpoint/sr-0409-arrival-verified分支，用于固定已经下载核验的生产提交a7b8a8d5f9b54cfa8000f3fcf3091ed0252b5511。继续开发分支是chat/sr-0402，最新修正不一定已进入本检查点JAR。

本轮主体：相邻机动车道及中央空间独立保护，精确车道口；普通MERGE目标车道从实际上游危险相交前关闭，到实际B完整接入后开放；修复目标高程钉死的一条原因。其他P1及全部P2未被本说明隐含标为完成。

真实JDK17 Forge1.20.1-47.4.20编译、旧回归、新32几何和78规划场景通过。下载artifact的摘要、CRC、COMMIT、源码字节与JAR版本已核实。详见PROGRESS.md、docs/checkpoints/arrival417-stage2-verified.json和docs/issues/problem2-batch3-status.md。

主分支排队的stage3最后两行旧Link编辑/距离显示修正另存WIP，并有本地6场景30检查；不能把它们与本检查点的Forge结果混在一起。原始构建源码ZIP仍为该生产提交的git archive，不加入后续文档来伪装同一快照。

存档37协议55，先备份世界，客户端/服务端同步。尚未运行Minecraft客户端、GPU/光影、世界写入、二进制保存重进或多人。对旧连接器的编辑兼容复测应等待最终补充CI确认。本检查点没有开展P2 terrain或多线程，也未实现地面绿化/高架全矩形封闭形态。

本分支的临时有写权限apply workflow已删除；固定基线补丁仅留历史，不可在新基线重跑。工作分支保留当前排队任务以免改动其分支头造成非快进冲突。
