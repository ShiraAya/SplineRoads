# Spline Roads 0.40.13-alpha

Minecraft 1.20.1 / Forge 47.4.20 / Java17。当前交付源码和JAR精确生产提交：`16cebd055dcf69e2605b8e3244fe81dbb8c220b2`；专用CI `37430977987` 已完成完整Forge构建和新旧回归，下载产物/摘要/源码字节已核验。本README等验收文档晚于该生产构建提交，不冒充在其源码ZIP内。

本轮恢复0.40.12自由匝道编辑入口、旧Link精确宽度和显式AUTO龙门架补正；新增连接器专属坡比：普通默认20%/超限25%，涉及高速默认原15%/超限20%，开关默认关闭。服务器按真实两端及关联链判定，并在生成、升降拟合及最终路面验证；自动立交不受影响。定向左转保留实际LEFT轨迹和请求诊断，不再漏出内部镜像求解的右转空间文字。

当前《问题2》仍全P1，《问题3》terrain及多线程全P2，必须先处理完P1。完整状态见 `docs/issues/problem2-batch6-status.md`，实际范围见 `docs/checkpoints/grade421-release.md`，构建证据见 `docs/checkpoints/grade421-verified.json`，续接见 `PROGRESS.md`。

```bash
bash tools/check_grade421.sh
```

该入口包含实际Forge compileJava/compileGameTestJava/jar/reobfJar；分层数学/规划/控件测试有显式地形、NBT和Widget适配器。没有真实Minecraft客户端、GPU/光影、方块写入、磁盘保存重进或多人验收。

**存档40/协议58。先备份世界，客户端和服务端同时更新，不用旧版打开新版保存数据。** 不在载入旧存档时全图重建；编辑旧连接会按新类型与规则检查。资源与许可保持。
