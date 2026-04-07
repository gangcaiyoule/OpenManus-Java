# 开发进度

## 当前阶段状态

- 日期：**2026-04-07**
- 阶段：**单 Agent 最小链路验收收口**
- 状态：**Blocked**
- 阶段边界：只收口“单 Agent + 上下文治理阶段 A + CodeAct 阶段 A + 工具结果摘要化 / 卸载索引 / 按需回填”的最小链路，不进入上下文治理阶段 B 后续切片 / C、MCP 资源融合或 `Multi-Agent` 默认实现面。
- 当前判断：本轮复验确认仓内实现仍与当前阶段目标对齐，`domain / agent / infra / aiframework` 分层边界未见新的回退；`./scripts/mvnw-local.sh -q -DskipTests compile` 与 `./scripts/mvnw-local.sh -q -DskipITs test` 已于 **2026-04-07 19:26 CST** 复验通过。当前唯一有效阻塞仍是外部 OpenAI-compatible `live smoke` 返回 `401` 鉴权错误。
- 提交判断：**当前不做阶段完成提交**。在外部 `live smoke` 转绿前，不新增阶段性 commit；如需记录协调动作，只允许提交文档级收口，不夹带新的实现扩张。

## 当前阻塞

1. 外部验收阻塞：`./scripts/run-live-smoke.sh` 于 **2026-04-07 19:27 CST** 复验失败，当前脱敏生效配置摘要为 `models=gpt-5.4, baseUrl=https://ruoli.dev/v1, apiKey=len=51,suffix=nLy0, source=models:default-llm,baseUrl:default-llm,apiKey:default-llm`。
2. 当前失败继续收敛为 OpenAI-compatible 渠道鉴权错误：`status=401`，错误摘要为 `无效的令牌`，`request id=202604071127112092223488268d9d6H4nOMkg6`。
3. `./scripts/mvnw-local.sh -q -DskipTests compile` 与 `./scripts/mvnw-local.sh -q -DskipITs test` 已通过，因此当前阻塞不归因于仓内编译、阶段内主线回归、测试覆盖缺口或分层守卫失败。
4. 当前工作区存在大量未提交改动；在外部验收未恢复前，这些改动不应继续向阶段外扩张，也不应混入新的功能性提交判断。

## 当前主线

1. 主线只保留一条：恢复外部 OpenAI-compatible 验收，不新增功能、不扩散重构。
2. 开发顺序第一步：只核对 `.env`、`OPENMANUS_LIVE_*`、`OPENMANUS_LLM_DEFAULT_LLM_*` 的生效值，确认 `baseUrl`、模型、凭证是否同源可用。
3. 开发顺序第二步：若配置修正后可形成同源组合，再仅重跑 `./scripts/run-live-smoke.sh`，不穿插仓内功能改动。
4. 开发顺序第三步：只有在配置确认无误后仍失败，才进入 OpenAI-compatible `client / transport / parser` 最小兼容层排查。
5. 在外部验收转绿前，当前阶段不进入上下文治理后续切片、MCP 资源融合或 `Multi-Agent` 默认实现面。

## 下一步入口

1. 先核对 `.env`、`OPENMANUS_LIVE_*`、`OPENMANUS_LLM_DEFAULT_LLM_*` 的实际生效值，确认 `https://ruoli.dev/v1`、`gpt-5.4` 与当前 `apiKey` 是否属于同一可用渠道。
2. 若发现渠道、模型或凭证错配，先修正外部配置，再仅重跑 `./scripts/run-live-smoke.sh`。
3. 若配置确认无误后 `live smoke` 仍失败，再把排查范围限制在 OpenAI-compatible `client/transport/parser` 最小兼容层，并保持当前仓内实现不扩散。
4. 只有在 `live smoke` 转绿后，才重新评估是否具备阶段完成提交条件；在此之前，若需提交，只允许文档级协调收口。
