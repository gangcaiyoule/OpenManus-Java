# 开发进度

## 当前阶段状态

- 阶段：阶段 A 收口
- 状态：**Blocked**
- 阶段边界：当前验收仍只收口“上下文治理 A + CodeAct A”的最小可运行链路；MCP、任务态增强和其他运行时扩展不作为本阶段完成依据。
- 当前结论：2026-04-04 本轮已复核 `./scripts/mvnw-local.sh -q -DskipITs test`、`cd frontend && npm test -- --run`、`cd frontend && npm run build`，均通过；`./scripts/run-live-smoke.sh` 仍因缺失 9 个 `OPENMANUS_LIVE_*` 变量在执行 Maven 前失败，阶段 A 仍缺真实 Provider 的 non-skipped 验收闭环。
- commit 判断：当前工作树包含大量尚未完成阶段验收的实现改动；本步只提交协调文档收敛，不混入代码主线或跨阶段内容。

## 当前阻塞

- 当前 shell 与仓库根目录 `.env` 仍未提供真实 Provider live smoke 所需的 9 个 `OPENMANUS_LIVE_*` 变量。
- 在这些变量补齐前，`./scripts/run-live-smoke.sh` 只能停在启动前校验，无法继续判断 Provider 连通性、鉴权或运行时缺陷。

## 当前主线

1. 保持阶段 A 验收口径不变，只收口真实 Provider live smoke 的 non-skipped 证据。
2. 当前可确认的主线结果仅包括：`./scripts/mvnw-local.sh -q -DskipITs test`、`cd frontend && npm test -- --run`、`cd frontend && npm run build` 通过；不据此扩展阶段目标。
3. 在 live smoke non-skipped 前，停止新增 MCP、任务态增强和其他运行时分支，避免继续偏离当前阶段边界。

## 下一步入口

1. 在当前 shell 或仓库根目录 `.env` 中补齐 OpenAI、Anthropic、Gemini 所需 9 个 `OPENMANUS_LIVE_*` 变量。
2. 复跑 `./scripts/run-live-smoke.sh`，记录 `tests / failures / errors / skipped` 与 `first issue`。
3. 若结果 non-skipped，再回填阶段 A 完成结论；若仍失败，只沿脚本输出收敛到具体 Provider、鉴权或传输层问题。
