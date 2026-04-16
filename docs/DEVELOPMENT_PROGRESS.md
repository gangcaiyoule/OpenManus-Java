# 开发进度

## 当前阶段状态

- 日期：**2026-04-16**
- 阶段：**单 Agent 最小链路验收收口**
- 状态：**In Progress / Blocked**
- 当前边界：只处理“单 Agent + 上下文治理阶段 A + CodeAct 阶段 A + 工具结果摘要化/卸载索引/按需回填 + MCP 工具发现/调用桥接”的验收收口；不进入 `Multi-Agent`、MCP 资源融合和上下文治理后续阶段。
- 当前验收口径：`./scripts/mvnw-local.sh -q -DskipTests compile`、`./scripts/mvnw-local.sh -q -DskipITs test`、`./scripts/run-live-smoke.sh`。
- 当前判断：仓内 `compile`、`test` 基线已稳定，当前阶段不再新增实现任务；阶段未收口的唯一原因仍是 `live smoke` 缺少 non-skipped 成功证据。

## 当前阻塞

- `live smoke` 仍被外部 TLS 环境阻塞。
- 当前首个真实阻塞是网关证书链未进入 live smoke 进程可用的 JVM 信任集。
- 该阻塞属于阶段验收环境缺口，不回推成新的默认运行时代码改造任务。

## 当前主线

1. 开发顺序保持不变：先收口单 Agent 验收，再判断阶段是否完成；在此之前不展开下一阶段规划。
2. 当前范围只允许处理验收闭环相关事项；已进入基线的会话 ID 规范化、上下文治理、CodeAct 和 MCP 工具桥接不再扩写新切片。
3. 当前主动作只剩 `live smoke` 环境闭环；TLS 问题只做进程级最小修正，不改默认运行时代码路径。
4. 进度文档继续只保留当前有效状态、当前阻塞、当前主线和下一步入口，不累积过期结论。

## 下一步入口

1. 为 `live smoke` 进程补齐 `OPENMANUS_LIVE_CA_CERT_FILE`，再次执行 `./scripts/run-live-smoke.sh`。
2. 若仍无法握手，再按 `OPENMANUS_LIVE_JDK_TLS_CLIENT_PROTOCOLS`、`OPENMANUS_LIVE_HTTPS_PROTOCOLS`、`OPENMANUS_LIVE_JAVA_TOOL_OPTIONS` 的顺序做进程级最小修正。
3. 取得 non-skipped 成功证据后，重新确认当前阶段是否满足最终收口条件，并再决定是否进入下一阶段计划。
