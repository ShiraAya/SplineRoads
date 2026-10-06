# Spline Roads 0.40.11-alpha — P1 匝道护栏与封闭槽位标线

Minecraft Java 1.20.1 / Forge 47.4.20 / Java 17。工作分支 `chat/sr-0402`，保留用户SR.zip编译修正后的基线。

**第一优先级是《问题2》全部24项；第二优先级是《问题3》terrain四项及多线程专项，必须在全部P1处理后开始。** [优先级](docs/PRIORITIES.md) · [完整当前状态](docs/issues/problem2-batch5-status.md) · [实时交接](PROGRESS.md)。用户实机反馈不能被旧离线通过记录覆盖。

本版按真实材料轮廓/孔区/高度保留并裁切连接器外侧护栏；兼容同高程接缝共享切面与末柱归属，关闭护栏、隔音墙、混合栏型和高度台阶不会冒领不存在的末柱。封闭槽位旁成为实际材料边缘的位置默认连续标线；双活车道虚线和显式线型覆盖保留。详见[范围与限制](docs/checkpoints/rail419-scope.md)。

最终源码/JAR对应 `645edcf67329d371fe5de2eef2b9e3e58dd6dad7`，GitHub Actions37420405833真实完整Forge构建成功，下载后逐字节和摘要核验。[最终证据](docs/checkpoints/rail419-verified.json)。本README是后续交接文档，不伪称已经包含于之前归档的源码ZIP。根SOURCE_BASELINE.json为历史0.40.1阶段记录，不是当前构建状态。

```powershell
.\gradlew.bat compileJava compileGameTestJava jar
```
```bash
bash tools/check_rail419.sh
```

测试适配器不进入JAR。GameTest类编译不等于世界运行；尚未实际测试Minecraft客户端、GPU/光影、方块写入、水流、磁盘保存重进、模组车辆或多人/FPS。

存档38、网络协议56不变。先备份世界，客户端/服务端同步更新，不并装两份SR。旧护栏/设施可能需要更新有关道路或连接才重新生成，不在载入时强制重建全图。本轮未新增所有孔内边的护栏、所有不同造型的通用过渡，Q2-21非封闭重合归属仍部分待处理。

保留任意槽位临时分离、整车道分离原限制、目标汇入封闭及矩形/地面绿化规则；无跨多实际路段的封闭传播。P2、多线程、独立匝道种类、默认龙门架/箭头和新坡比规则没有被悄悄提前实现。历史一次性apply脚本禁止在后续源码重跑。
