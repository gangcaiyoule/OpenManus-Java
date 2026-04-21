# 开发进度

## 当前阶段状态

- 日期：**2026-04-16**
- 阶段：**单 Agent 最小链路验收收口**
- 状态：**In Progress / Blocked**
- 当前边界：只收口“单 Agent + 上下文治理阶段 A + CodeAct 阶段 A + 工具结果摘要化/卸载索引/按需回填 + MCP 工具发现/调用桥接”的验收闭环；不进入 `Multi-Agent`、MCP 资源融合和上下文治理后续阶段。
- 当前验收口径：`./scripts/mvnw-local.sh -q -DskipTests compile`、`./scripts/mvnw-local.sh -q -DskipITs test`、`./scripts/run-live-smoke.sh`。
- 当前判断：`2026-04-16` 已再次实际复验 `compile`、全量 `test` 与 `live smoke`。仓内实现、分层守卫和当前阶段默认测试口径保持稳定，当前阶段唯一未闭环项仍是外部 `live smoke` TLS 证书链环境。

## 当前阻塞

- `live smoke` 仍被外部 TLS 环境阻塞，当前缺少 non-skipped 成功证据；首个真实阻塞继续收敛为 `PKIX path building failed`。

## 当前主线

1. 当前主线只剩 `live smoke` 环境证据收口；仓内实现、分层守卫和默认测试口径保持冻结，不再新增功能切片。
2. 当前开发顺序固定为：先补齐 `OPENMANUS_LIVE_CA_CERT_FILE` 并重跑 `./scripts/run-live-smoke.sh`，再视结果决定是否追加 TLS 协议参数；未拿到 non-skipped 成功证据前，不切入下一阶段。
3. 当前范围只允许处理验收闭环，不新增上下文治理、CodeAct、MCP 工具桥接或会话链路之外的新实现。
4. TLS 问题只做进程级最小修正，不改默认运行时代码路径，不把环境缺口回推成新的架构扩展任务。

## 下一步入口

1. 阶段 1：为 `live smoke` 进程补齐 `OPENMANUS_LIVE_CA_CERT_FILE`，重新执行 `./scripts/run-live-smoke.sh`，优先拿到 non-skipped 成功证据。
2. 阶段 2：若仍无法握手，再按 `OPENMANUS_LIVE_JDK_TLS_CLIENT_PROTOCOLS`、`OPENMANUS_LIVE_HTTPS_PROTOCOLS`、`OPENMANUS_LIVE_JAVA_TOOL_OPTIONS` 的顺序做进程级最小修正，直到外部 TLS 阻塞被确认收口或明确无法在当前环境解除。
3. 阶段 3：只有在 `live smoke` 拿到 non-skipped 成功证据后，才允许判断“单 Agent 最小链路验收收口”是否完成，并再决定是否进入下一阶段计划。
