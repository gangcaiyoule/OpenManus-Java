# 开发进度

## 当前阶段状态

- 阶段：阶段 A 收口（live smoke 证据闭环）
- 状态：**Blocked（仅外部门禁）**
- 阶段完成判定：`*LiveSmokeTest` 产出 non-skipped 结果，并回填 surefire 三项统计。

## 当前阻塞

- 同一会话未注入 OpenAI / Anthropic / Gemini 三组 `OPENMANUS_LIVE_*` 变量。
- live smoke 暂无法产出真实 Provider 的 non-skipped 结果。

## 当前主线

1. 先补齐三组 `OPENMANUS_LIVE_*` 变量并在同一会话生效。
2. 仅执行 `mvn -q -DskipITs test -Dtest='*LiveSmokeTest'` 验证真实 Provider 链路。
3. 回填当前轮 surefire `tests/failures/skipped` 与首个失败分类。
4. 只有在 non-skipped 证据形成后，才进入 `domain/service -> UnifiedWorkflow` 最小端口化收敛。

阶段边界：
- 本阶段不进入上下文治理 B/C。
- 本阶段不进入 MCP 新能力开发。
- 本阶段不做跨层重构与提前抽象。

## 下一步入口

1. 入口动作：准备并注入三组 `OPENMANUS_LIVE_*` 变量。
2. 执行动作：运行 `mvn -q -DskipITs test -Dtest='*LiveSmokeTest'`。
3. 结果动作：更新本文件为最新状态（仅保留当前阶段状态、当前阻塞、当前主线和下一步入口）。
4. 提交动作：本轮仅在“进度文档有实质更新”时提交文档；代码改动等待下一阶段任务单独提交。
