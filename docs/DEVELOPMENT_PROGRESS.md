# 开发进度

## 当前阶段状态

- 日期：**2026-04-07**
- 阶段：**单 Agent 最小链路验收收口**
- 状态：**Blocked**
- 验收口径：当前阶段只以 `compile`、Java `test`、前端 `test`、`live smoke` 四项结果判断是否收口。
- 当前结果：截至 `2026-04-07 12:08 CST`，`./scripts/mvnw-local.sh -q -DskipTests compile`、`./scripts/mvnw-local.sh -q -DskipITs test` 与 `cd frontend && npm test -- --run` 已复验通过；`./scripts/run-live-smoke.sh` 仍失败，失败为 OpenAI-compatible 主链路 `gpt-5.4` 候选返回确定性 `401` `无效的令牌`，request id 为 `20260407040805149637618268d9d62czZSkcA`。
- 当前结论：本轮未发现新的仓内实现缺口，阶段阻塞继续收敛为外部 OpenAI-compatible 渠道鉴权；当前协调动作只保留验收阻塞排查，不进入额外功能开发、跨阶段整理或结构扩张。
- 阶段边界：本阶段只收口“单 Agent + 上下文治理阶段 A + CodeAct 阶段 A + 工具结果摘要化 / 卸载索引 / 按需回填”的最小可运行链路；不进入上下文治理阶段 B 后续切片 / C、MCP 资源融合或 `Multi-Agent` 默认实现面。

## 当前阻塞

- `./scripts/run-live-smoke.sh` 在 `2026-04-07 12:08 CST` 复验失败，失败摘要为：`attempted=[gpt-5.4]`，`status=401`，`message="无效的令牌"`，request id 为 `20260407040805149637618268d9d62czZSkcA`。
- 当前真实阻塞已收敛为外部鉴权 / 凭证配置，不再是仓内 `compile`、`test`、前端测试或 `.env -> OPENMANUS_LLM_DEFAULT_LLM_* -> OPENMANUS_LIVE_*` 回填链路问题。
- 当前工作区改动仍明显偏大，最新 `git diff --shortstat` 为 `105 files changed, 6831 insertions(+), 7397 deletions(-)`；在 `live smoke` 未恢复前，不进入新功能扩展，也不做跨阶段整理型提交。

## 当前主线

- 主线一：冻结当前实现面，不新增功能，不扩展到上下文治理阶段 B 后续切片 / C、MCP 资源融合或 `Multi-Agent` 默认实现。
- 主线二：只处理当前外部验收阻塞，优先修正 OpenAI-compatible 渠道 `api key`，不改动现有 `base URL`、模型 `gpt-5.4` 和仓内主链路实现。
- 主线三：`live smoke` 恢复前，不整理大工作区代码，不切跨阶段代码提交；当前提交判断为“不进行代码提交”，只允许同步当前有效验证结果、阻塞信息和下一步入口。

## 下一步入口

1. 先修正当前 `.env` 命中的 OpenAI-compatible 渠道 `api key`，保持现有 `base URL` 和模型 `gpt-5.4` 不变。
2. 修正后只重跑 `./scripts/run-live-smoke.sh`，确认结果是否从确定性 `401` 转为成功或新的非鉴权错误。
3. 若仍失败，只补最新 request id、状态码和错误摘要，继续停在当前阶段，不把兼容逻辑扩散到 `domain`、Controller 或配置装配层。
4. 仅在四项验收全绿后，再进入工作区收敛、提交切分和阶段收口；提交顺序先做最小验收收口，再决定是否拆出后续整理提交。
