# 开发进度

## 当前阶段状态

- 日期：**2026-05-07**
- 阶段：**文件级动态装载收尾已完成**
- 状态：**Stage Complete**
- 当前边界：单 Agent + 上下文治理阶段 A/B + CodeAct 阶段 A + 工具结果摘要化/卸载索引/按需回填 + MCP 工具发现/调用桥接已验收闭环。
- 当前验收口径：`./scripts/mvnw-local.sh -q -DskipTests compile` ✅、`./scripts/mvnw-local.sh -q -DskipITs test` ✅。

## 已完成项

1. 上下文治理阶段 A：上下文来源明确、组装顺序统一、基础预算控制建立。
2. 上下文治理阶段 B 第一切片：工具结果摘要化、卸载索引与按需回填。
3. CodeAct 阶段 A：计划 -> 执行工具 -> 观察结果 -> 调整计划循环闭环。
4. MCP 工具发现与调用桥接：`McpToolRegistryBootstrap` 发现并注册 MCP tools。
5. 文件级动态装载收尾：旧 artifact / rehydrate / snapshot 链路已清理，收敛为"工具原始结果 + API 前统一预算 + Shell 显式读取"。
6. 旧链路清理：`FileReadTool`、`ContextBudgetPolicy`、`HistoricalContextSummarizer`、`ModelContextTokenCounter`、artifact store、indexed rehydrate 已删除。
7. 前端目录迁移：`front/` -> `frontend/`。

## 当前阻塞

- `live smoke` 仍被外部 TLS 环境阻塞，当前缺少 non-skipped 成功证据；首个真实阻塞收敛为 `PKIX path building failed`。
- 此阻塞为外部环境问题，不影响仓内实现和默认测试口径的持续推进。

## 下一阶段候选

1. **Live smoke 环境收口**：补齐 `OPENMANUS_LIVE_CA_CERT_FILE` 并重跑 `./scripts/run-live-smoke.sh`，拿到 non-skipped 成功证据。
2. **上下文治理阶段 C**：将"计划、执行中间态、待办、失败原因"从普通聊天历史中结构化抽离。
3. **MCP 资源融合**：支持资源读取、上下文注入、会话级能力缓存。
4. **Web snapshot 前端迁移**：前端不再消费旧 WebFetch snapshot 字段，改为展示原始正文。
5. **Shell 命令安全策略**：评估 Shell 命令级校验，定义读文件命令范围与 Docker sandbox 硬化。
6. **Multi-Agent 协同研究**：研究多 Agent 协同执行模式（任务拆分、角色分工、上下文传递与结果汇总）。