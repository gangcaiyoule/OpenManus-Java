# 开发进度

## 当前阶段状态

- 日期：**2026-04-07**
- 阶段：**单 Agent 最小链路验收收口**
- 状态：**Blocked**
- 验收口径：当前阶段只以 `compile`、Java `test`、前端 `test`、`live smoke` 四项结果判断是否收口。
- 当前结果：截至 `2026-04-07 11:43 CST`，`./scripts/mvnw-local.sh -q -DskipTests compile`、`./scripts/mvnw-local.sh -q -DskipITs test` 与 `cd frontend && npm test -- --run` 已复验通过；`./scripts/run-live-smoke.sh` 仍失败，失败为 OpenAI-compatible 主链路 `gpt-5.4` 候选返回确定性 `401` `无效的令牌`，request id 为 `202604070343109055795988268d9d6H1Vtgkvm`。
- commit 判断：**本轮仅提交文档协调更新**
- 判断依据：阶段实现面仍处于外部验收阻塞，不做功能提交；但当前需要把主线、边界和下一步入口继续收敛到文档，便于后续按同一节奏推进。
- 阶段边界：本阶段只收口“单 Agent + 上下文治理阶段 A + CodeAct 阶段 A + 工具结果摘要化 / 卸载索引 / 按需回填”的最小可运行链路；不进入上下文治理阶段 B 后续切片 / C、MCP 资源融合或 `Multi-Agent` 默认实现面。

## 当前阻塞

- `./scripts/run-live-smoke.sh` 在 `2026-04-07 11:43 CST` 复验失败，失败摘要为：`attempted=[gpt-5.4]`，`status=401`，`message="无效的令牌"`，request id 为 `202604070343109055795988268d9d6H1Vtgkvm`。
- 当前真实阻塞已收敛为外部鉴权 / 凭证配置，不再是仓内 `compile`、`test` 或前端测试问题。
- 当前工作区改动仍明显偏大，`git diff --shortstat` 仍为 `105 files changed, 6851 insertions(+), 7406 deletions(-)`；在 `live smoke` 未恢复前，不进入新功能扩展，也不做跨阶段整理型提交。

## 当前主线

- 主线一：当前实现面冻结，不新增功能，不扩大到上下文治理阶段 B 后续切片 / C、MCP 资源融合或 `Multi-Agent` 默认实现。
- 主线二：优先核对 `.env -> OPENMANUS_LLM_DEFAULT_LLM_* -> OPENMANUS_LIVE_*` 回填链路中的 OpenAI-compatible `api key`、`base URL`、模型名是否与当前渠道一致。
- 主线三：若 `live smoke` 继续失败，只允许围绕候选模型、响应码、错误摘要和配置链路做最小排查，不把兼容逻辑扩散到 `domain`、Controller、配置装配层。
- 主线四：只有在四项验收全绿后，才进入工作区收敛、提交面压缩和阶段提交判断。

## 下一步入口

1. 先修正或确认 live smoke 当前使用的 OpenAI-compatible 凭证与渠道配置。
2. 修正后重新执行 `./scripts/run-live-smoke.sh`，只观察是否解除确定性 `401`。
3. 若仍失败，继续记录最小诊断信息并停在当前阶段，不转入新实现。
4. 若四项验收全绿，再按“小步推进”原则拆分并整理后续提交。
