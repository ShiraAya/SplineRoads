# SR 当前优先级入口（用户最新指令）

1. 第一优先级：`issues/problem2-current-priority.md`，Q2-01～Q2-24 全部。
2. 第二优先级：`issues/problem3-second-priority.md`，terrain 草地路面闪烁、标线埋入、路灯黑块、开启光影仍重载，加上多线程/异步建造专项。

必须在所有第一优先级问题处理完后再开始第二优先级；不要提前穿插 terrain 或多线程优化。已有0.40.6优化不回滚，第一优先级缺陷修复所必需的变更仍可进行。

开始工作同时读取 AGENTS.md、PROGRESS.md、CURRENT_REQUIREMENTS.md、此文件及相应问题清单。旧 Q2-06 高Y/远景虚空仍为 P1；新附件只增加第二优先级队列。状态登记不代表完成，旧编译/离线测试不能覆盖新实机反馈。
