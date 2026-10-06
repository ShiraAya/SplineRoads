# SR-0.40.15 repair — first candidate saved, full CI pending

User authorized implementation on 2026-10-06. Candidate version 0.40.16-alpha-stage1; branch remains SR-0.40.15.

Stage1 actual RoadIndex reference/mesh preservation and live lane identity are retained. Stage2 corrects the closed-neighbor receiver guard found by the first full CI, adds union planter end caps, exposed hole-end rails, upstream X warnings, whole-span corridor targets and readable failure details. Actual continuation and asymmetric creation UI remain unfinished. See docs/checkpoints/SR15-stage2-426.md for all 14 statuses.

Run bash tools/check_sr426.sh. Full Java17 Forge and regression result is pending at this commit; no Minecraft runtime/original world acceptance. Stage1 run 37473665897 compiled with Forge but failed a genuine receiver-protection regression; that production guard was corrected and the unchanged rejection test passed locally. Do not label the failed stage1 run a full success.

Next priority: SR15-09/10 endpoint continuation against current physical cross-sections without double trimming authored geometry; SR15-11 per-direction 1-4 lane controls; then SR15-03/07/08. No other branch modified.

---
## Historical checkpoint
# SR-0.40.15 repair in progress — stage1 saved

User authorized implementation on 2026-10-06. Base 376ceae.

Saved actual RoadIndex canonical-mesh/reference fix, current-station live-lane/outside identity, sequential merge validation and final lane-point reconciliation. See docs/checkpoints/SR15-stage1-425.md. Local old regression and new actual-index adapter regression passed. The same new test fails against old RoadIndex with lost reference. Full CI is pending; NOT all 14 issues fixed and NOT Minecraft acceptance. Per-direction creation sliders and actual endpoint continuation remain unfinished.

Current entry: bash tools/check_sr425.sh. Continue directly on SR-0.40.15; no changes to other branches.

---
## Historical pre-implementation checkpoint
# SR-0.40.15：新一轮实机反馈已归档，尚未开始修复

日期：2026-10-06。用户要求本轮只整理 11 张截图及文字反馈，并将现有 0.40.15 源码保存在新分支，下一轮再修复。

## 当前基线

仓库：ShiraAya/SplineRoads；工作分支：SR-0.40.15。

实际版本：0.40.15-alpha-p1.1。源码提交：61400caa43c89d4fd2c4b6d17fe80ca9b8cf93d9。

分支从 f5b2cfc29f55de038e2108d7b88e99f267cdfdc0 创建；与源码提交相比只有历史验证文档的差别，代码一致。源码 ZIP 已核对 SHA-256，并由其中 1798 个文件重算 Git tree 为 dc69468166ecb7f99d8914af2b7ac310835a75c0。

## 下一轮入口

先读 docs/feedback/SR-0.40.15-2026-10-06.md。原始文字、截图索引、源码基线分别保存在同目录的 original.md、screenshots.json 以及 docs/checkpoints/SR-0.40.15-baseline.json。

条目 SR15-01 至 SR15-14 全部待修复，不能因为旧自动测试通过就标为解决。核心新增要求是实时端点断面、合并后车道点居中与外侧身份更新，以及高速/普通→单向/双向→每方向1–4车道滑块，支持双向2+1。

11 张原始 PNG 随本轮下载的问题包保存；GitHub 源码树保存截图编号与校验索引，不包含这 11 张 PNG。本轮没有用户原始世界存档，不得将截图玩家坐标视为道路端点坐标。

本轮仅写文档和创建分支；未修改 Java、资源、构建脚本或版本，未开始修复，未重新编译。main、SR-0.40.14、chat/sr-0402 未改动。后续等待用户下一轮指令，从这个分支继续，不回退到0.40.14。

## 历史记录

上一轮进度保留在提交 f5b2cfc29f55de038e2108d7b88e99f267cdfdc0 的 PROGRESS.md；上一轮验证记录仍在 docs/checkpoints/FINAL_VERIFICATION_424.md。其成功构建不覆盖本轮截图中报告的缺陷。terrain、光影和多线程旧待办不在本轮自动扩展范围内。
