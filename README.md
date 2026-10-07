# Spline Roads 0.40.21-alpha — 匝道自动选线修复

Minecraft 1.20.1 / Forge 47.4.20 / JDK 17。基于用户提供的 0.40.20-alpha-rc1 源码；项目包含 AI 生成和修改的代码。

本版补全自动回转的四种圆弧—直线—圆弧方向组合，优先尝试可行短转弯和沿车道的直行过渡，再考虑回环；修正失败原因归属。保持所选车道方向、最小半径、逐段坡比、路面体积、净空及车道恢复保护。

测试夹具：501个方块位置的两条道路（端点中心跨度500m），中点垂直交叉，高差8m，双向六车道。A额外扩出、B/C/D保留车道分离；1/8额外扩入、2–7并入所选现有车道并暂闭恢复。各项独立测试。

本轮真实Minecraft服务端指定矩阵32/32、固定同端点追加矩阵32/32、四组模型128/128通过，完整Forge构建通过。

完整测试记录见 `SR_0_40_21_VALIDATION.md`；不要把模型验证理解为客户端画面或用户存档验收。协议保持65，保存格式不变。

```bash
bash tools/test_ramp39_model.sh
python3 tools/run_matrix434.py --out build/matrix434-ordinary
python3 tools/run_matrix434.py --out build/matrix434-highway --type highway
python3 tools/run_matrix434.py --out build/matrix434-east --end east --flexible
bash gradlew compileJava compileGameTestJava jar
bash gradlew runGameTestServer -ProadRunDir=run434 -ProadTestNamespaces=splineroads_matrix434
```

以下为原上传包历史说明，版本及验证范围以本页顶部和本版验证记录为准。

---

# Spline Roads 0.40.14-alpha — Q2统一实机候选

Minecraft Java1.20.1 / Forge47.4.20 / JDK17。精确源码与JAR：e87cec55171ed247db78c8e7951c5242713ed164；最终CI37437611241完整构建及回归通过，下载产物已核验。证据见 [验证记录](docs/checkpoints/q2close422-verified.json)，全部24项与新操作见 [统一实机清单](docs/issues/problem2-unified-0414-test.md)。

剩余Q2-01/06/19与10/17/21本轮一并实现：混宽盲道、世界相对VBO雾距、独立外侧合流缩减形成补入空位、局部端口坡度、半幅支承分类和Link共面标线归属。其余Q2既有实现保留并回归；并非用户原存档已验收。Q3 terrain与多线程继续搁置。

新操作在手动蓝色车道点选择“外侧合流缩减”，默认32格；先放好下游补入目标点，再缩减及REPLACE。原整车道分离限制不变，临时保留分离仍任意槽位。同向唯一车道不可缩减，也不可导入已关闭邻道。

存档41/协议59。先备份世界副本，客户端/服务端同时更新，不同时装两份SR，不用旧版打开新版保存数据。旧设施可能需明确更新，不全图载入重建。

```bash
bash tools/check_q2final422.sh
```

该入口包含旧回归、完整Forge四项构建与新六项测试。明确的Ground/NBT/index适配器不进入生产包。未运行真实Minecraft/GLSL/GPU、世界写入、水流、磁盘保存重进、车辆、多人或FPS。先依统一实机清单验收，再处理新反馈。
