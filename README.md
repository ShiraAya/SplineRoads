# Spline Roads 0.40.1-alpha — 渲染切换、普通道路编辑和分块队列热修复

Minecraft Java 1.20.1 · Forge 47.4.20 · Java 17。**本轮基准是用户上传的 0.40 compile-fixed 源码，不是未经用户修复的上一份包。**

完整源码测试版；本环境没有完成完整 Forge 编译，也没有 Minecraft／Oculus／GPU 实测，不含发行 JAR。Gradle wrapper 在下载分发包时遇到 DNS 失败，尚未执行编译任务。离线源码／模型回归已经执行，不能替代实机。

## 本版修改

- `AUTO` 根据 Iris／Oculus 的实际 shader pack 渲染状态，在无光影 VBO 与有光影 terrain 之间切换。不再仅检查模组是否安装。保留显式 VBO／TERRAIN 覆盖。
- 普通道路的操作只处理本次改变的车道依赖和接头；不再让远处旧匝道的路线重算失败阻断无关道路的建造、更新和删除。
- 区块载入／卸载不再触发整条道路和设施 VBO 重建。terrain 使用道路／区块瓦片队列、去重、已卸载瓦片的有界 LRU 缓存；道路改变、删除和资源重载仍正确失效。

详见 [修复说明与源码入口](SR_0_40_1_FIXES.md)、[验证结果与实机复测表](SR_0_40_1_VALIDATION.md)、[修改文件清单](SR_0_40_1_CHANGED_FILES.txt)。

## 使用与构建

在实例 `config/splineroads-client.toml` 保持：

```toml
[rendering]
    surfaceBackend = "AUTO"
```

此前为了测试手动设成 `TERRAIN` 的，需要改回 `AUTO`。不要删除整个配置或道路存档。切换光影或资源重载会有一次正常的模型刷新，不承诺零耗时切换。

JDK 17，在联网构建环境运行：

```powershell
.\gradlew.bat compileJava compileGameTestJava validateRamp39 validateLaneRampWorkflow validatePerformance40 jar
```

具备 Bash 的环境可以执行本轮离线测试：

```bash
bash tools/test_hotfix401.sh
```

`tools/` 下的 Forge／Minecraft／Iris 适配器与假 API 不加入生产源集或模组 JAR。真实构建使用原项目依赖，不替换生产 API。

先使用存档副本。客户端和服务端建议同步更新；存档格式 **34**、网络协议 **52** 不变。本轮没有增加通用收费广场或取消既有匝道保护，也不保证原存档中每条旧匝道已自动修好。

0.39 的分离、补入、方向样式、精确／弹性 B 等功能与原限制保留。历史记录见 [0.40 README](README_0_40_HISTORY.md)；其中“已安装即 terrain”的旧 AUTO 语义已被本版替代。
