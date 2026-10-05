# Spline Roads 0.40.6-alpha

Minecraft Java1.20.1 / Forge47.4.20 / Java17。沿用用户修好编译的SR.zip与0.40.5远端代码，分支chat/sr-0402。

本版减少建造/更新的重复结构栅格，复用事务内按原顺序返回的局部结构候选，并复用同次地形碰撞形状。没有关闭净空/碰撞/权限检查，没有把世界或GPU写入扔进后台。

隧道仅黄线/护栏（GREEN实际转护栏，无洞内植物）、TEMPORARY任意所选车道及安全恢复、DETACH原限制、VBO/terrain有效缓存复用保留。

[本轮细节与限制](docs/checkpoints/2026-10-05-0406-performance.md) / [问题状态](docs/issues/2026-10-05-resolution-status.md) / [实际进度](PROGRESS.md)。未以核心微基准推算真实存档建造/FPS提速。U02新材质来源仍无实际资产可核对。

```powershell
.\gradlew.bat compileJava compileGameTestJava jar
py tools/summarize_profile.py "路径\latest.log" --output sr-profile-summary.json
```
启用详细计时用JVM参数`-Dsr.profile=true`。汇总工具本地运行，不复制原日志行；connect/edit嵌套记录不能相加。

```bash
bash tools/check_release0406.sh
python tools/test_profile_summary.py
```
测试适配器不进JAR。构建、核心测试与实机分开报告；未运行Minecraft/GPU/光影/多人/真实存档写入验收。存档36协议54保持，先备份并同步替换客户端/服务端。一次性历史apply脚本不可重跑。
