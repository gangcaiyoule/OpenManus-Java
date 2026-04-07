# 开发进度

## 当前阶段状态

- 日期：**2026-04-07**
- 阶段：**单 Agent 最小链路验收收口**
- 状态：**Blocked**
- 阶段边界：当前实现仍收敛在“单 Agent + 上下文治理阶段 A + CodeAct 阶段 A + 工具结果摘要化 / 卸载索引 / 按需回填”的最小链路内，不进入上下文治理阶段 B 后续切片 / C、MCP 资源融合或 `Multi-Agent` 默认实现面。
- 当前基线：`2026-04-07 15:55 CST` 的 `compile`、默认 Java `test` 与前端 `test` 已通过；`2026-04-07 15:56 CST` 的 `./scripts/run-live-smoke.sh` 仍失败，失败摘要为 `status=401`、`message="无效的令牌"`。
- 边界复验：`2026-04-07 16:08 CST` 已补充复验 `SingleAgentArchitectureGuardTest`、`ContextAssemblerTest`、`WorkflowStreamServiceMonitoringIntegrationTest`、`McpRuntimeConfigWiringTest`、`WebProxyControllerConditionTest`、`AgentControllerServiceContractTest`、`SessionSandboxManagerLifecycleTest`，当前分层、上下文治理、MCP 开关、Web 代理装配、监控收口与会话沙箱编排边界未回退。

## 当前阻塞

- 唯一有效阻塞仍是 OpenAI-compatible `live smoke` 的外部鉴权失败。
- 当前应优先核对实际命中的 `base URL`、模型与 `api key` 是否属于同一渠道；在该问题解决前，不扩展新的实现面。

## 当前主线

- 主线顺序固定为：核对 `live smoke` 实际命中配置 -> 修正外部鉴权 -> 重跑 `./scripts/run-live-smoke.sh` -> `live smoke` 转绿后再统一确认四项验收。
- 当前阶段只允许围绕 `live smoke` 阻塞推进，不新增功能、不切入下一阶段、不把兼容修正扩散到 `domain`、Controller 或配置层。

## 下一步入口

1. 只处理 `live smoke` 实际命中配置与外部鉴权问题。
2. 修正后仅重跑 `./scripts/run-live-smoke.sh`。
3. `live smoke` 转绿后，再统一确认四项阶段验收是否全部满足。
