# 开发进度

## 当前阶段状态

- 日期：**2026-04-07**
- 阶段：**单 Agent 最小链路验收收口**
- 状态：**Blocked**
- 验收口径：当前阶段只以 `compile`、Java `test`、前端 `test`、`live smoke` 四项结果判断是否收口。
- 当前结果：截至 `2026-04-07 11:59 CST`，`./scripts/mvnw-local.sh -q -DskipTests compile`、`./scripts/mvnw-local.sh -q -DskipITs test` 与 `cd frontend && npm test -- --run` 已复验通过；`./scripts/run-live-smoke.sh` 仍失败，失败为 OpenAI-compatible 主链路 `gpt-5.4` 候选返回确定性 `401` `无效的令牌`，request id 为 `202604070358575317202978268d9d6QDDt9HfP`。
- commit 判断：**进行一次文档提交，仅提交当前进度同步；不提交代码改动**
- 判断依据：当前阶段主线未变化，阻塞仍是外部鉴权；仓内还有大规模未收敛代码改动，不能在 `live smoke` 未恢复时做跨阶段代码提交，但需要把协调结论固化到文档，便于后续按同一边界继续推进。
- 阶段边界：本阶段只收口“单 Agent + 上下文治理阶段 A + CodeAct 阶段 A + 工具结果摘要化 / 卸载索引 / 按需回填”的最小可运行链路；不进入上下文治理阶段 B 后续切片 / C、MCP 资源融合或 `Multi-Agent` 默认实现面。

## 当前阻塞

- `./scripts/run-live-smoke.sh` 在 `2026-04-07 11:59 CST` 复验失败，失败摘要为：`attempted=[gpt-5.4]`，`status=401`，`message="无效的令牌"`，request id 为 `202604070358575317202978268d9d6QDDt9HfP`。
- 当前真实阻塞已收敛为外部鉴权 / 凭证配置，不再是仓内 `compile`、`test`、前端测试或 `.env -> OPENMANUS_LLM_DEFAULT_LLM_* -> OPENMANUS_LIVE_*` 回填链路问题。
- 当前工作区改动仍明显偏大，`git diff --shortstat` 为 `105 files changed, 6843 insertions(+), 7407 deletions(-)`；在 `live smoke` 未恢复前，不进入新功能扩展，也不做跨阶段整理型提交。

## 当前主线

- 主线一：冻结当前实现面，不新增功能，不扩展到上下文治理阶段 B 后续切片 / C、MCP 资源融合或 `Multi-Agent` 默认实现。
- 主线二：只处理当前外部验收阻塞，优先修正 OpenAI-compatible 渠道 `api key`，不改动现有 `base URL`、模型 `gpt-5.4` 和仓内主链路实现。
- 主线三：`live smoke` 恢复前，不整理大工作区代码，不切代码提交；本轮只保留文档级协调提交。

## 下一步入口

1. 先修正当前 `.env` 命中的 OpenAI-compatible 渠道 `api key`，保持现有 `base URL` 和模型 `gpt-5.4` 不变。
2. 修正后只重跑 `./scripts/run-live-smoke.sh`，确认结果是否从确定性 `401` 转为成功或新的非鉴权错误。
3. 若仍失败，只补最新 request id、状态码和错误摘要，继续停在当前阶段，不把兼容逻辑扩散到 `domain`、Controller 或配置装配层。
4. 仅在四项验收全绿后，再进入工作区收敛、提交切分和阶段收口。
