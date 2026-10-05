# Spline Roads — chat/sr-0402 开发检查点

Minecraft Java 1.20.1 · Forge 47.4.20 · JDK 17。

**当前分支已包含 R1 过渡标线修复和 R2 双向最外侧整车道分离。GitHub Actions 的完整 Forge 编译、GameTest 源码编译、JAR/reobfJar 和核心/模型回归已通过。** 这仍是阶段测试，不是 Minecraft/光影实机验收或完整 0.40.2 发布；内部版本号暂沿用 0.40.1-alpha。

## 当前状态

- 过渡标线：高架断面调整保留车道分界线的对应关系；覆盖增减车道、真实缩窄端点、两端连接、左右行驶、地面/高架等 966 个合法组合。
- 双向整车道分离：支持各行驶方向最外侧车道，对向车道和世界中央隔离轴保持不变。不是只删除“单向限制”的检查。
- “保留车道分离”的暂时取消后恢复：**尚未实现**。
- VBO/terrain 后端切换的有界热缓存复用：**尚未实现**。

生产代码最后更新：`9c0cb386dc3971d257b1f0cf286b01fb390f3b02`；最终增强测试提交：`782365d7a9be91f79d1553ad8386025dcb3099d7`；Actions run `37304622929`。

详细结果、限制、校验和及下一轮入口：[PROGRESS.md](PROGRESS.md)、[R1 记录](docs/checkpoints/2026-10-05-R1.md)、[R2 记录](docs/checkpoints/2026-10-05-R2.md)、[最终验证](docs/checkpoints/2026-10-05-VERIFIED.md)。

## 构建与测试

```powershell
.\gradlew.bat compileJava compileGameTestJava jar
```

```bash
bash tools/test_transition402.sh
bash tools/test_bidirectional402.sh
```

离线脚本的显式 Minecraft/Forge/API 适配器位于 tools，不打入模组 JAR。真实构建使用原 Forge 依赖。常规 GitHub Actions 只读仓库，在该工作分支源码变更后执行回归和完整构建、保留日志/JAR。

## 保留的 0.40.1 功能

AUTO 仍按实际光影开启状态在 VBO 与 terrain 间切换；普通道路操作采用局部车道依赖范围；区块流式载入采用分块准备。这轮还没有修改后端切换缓存，切换时原有刷新仍可能发生。

配置 `config/splineroads-client.toml`：

```toml
[rendering]
    surfaceBackend = "AUTO"
```

先备份并使用世界副本测试，只装一份 SR。客户端和服务端建议同步替换检查点；存档格式34/协议52暂未修改不表示不同代码版本混用或回退已经验证。没有新增收费广场或取消原有保护/净空检查。

原版本说明作为历史保留：[0.40.1修复](SR_0_40_1_FIXES.md)、[0.40.1验证](SR_0_40_1_VALIDATION.md)、[0.40 README](README_0_40_HISTORY.md)。历史文档中的“未完成Forge编译”只描述当时环境；当前分支的真实 CI 结果以上述最新记录为准。
