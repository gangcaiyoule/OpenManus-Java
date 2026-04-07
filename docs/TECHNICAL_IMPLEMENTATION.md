# 技术实现方案

## 1. 当前阶段范围

当前阶段只保留以下实现面：

1. 上下文治理阶段 A。
2. CodeAct 阶段 A。
3. 上下文治理阶段 B 第一切片：工具结果摘要化、卸载索引与按需回填。

当前不进入：

- 上下文治理阶段 B 后续切片 / C。
- MCP 资源融合。
- `Multi-Agent` 默认实现面。

当前阶段完成标准：

1. 单 Agent 最小链路稳定。
2. `./scripts/mvnw-local.sh -q -DskipTests compile` 通过。
3. `./scripts/mvnw-local.sh -q -DskipITs test` 通过。
4. `./scripts/run-live-smoke.sh` 产出 non-skipped 成功结果。

## 2. 当前有效分层

- `domain` 只保留业务语义与 port。
- `agent` 负责单 Agent 执行编排、上下文治理、CodeAct 最小闭环。
- `infra` 负责配置、存储、监控、沙箱、Web 入口与 adapter。
- `aiframework` 负责模型、消息、工具与 provider 运行时抽象。

当前必须守住的边界：

- `domain` 不承载 Spring Web / Servlet / 调度 / 条件装配语义。
- `domain/service` 只通过 port 调工作流、监控、代理与沙箱能力。
- `infra` 负责 `Controller`、配置装配、外部协议适配和运行时细节。
- MCP 当前只保留“工具发现 + 工具调用协议转换”；`mcp.resource.read` 默认关闭。

## 3. 当前最小可运行链路

### 3.1 上下文治理

- `ContextSnapshot` 拆分历史消息、当前轮消息和当前用户输入。
- `ContextBudgetPolicy` 统一消息数量与总体积预算。
- `ContextAssembler` 按“历史 -> 当前轮 -> 总量裁剪”组装模型输入，并在缺少当前轮用户锚点时补回用户消息。
- `ToolResultContextCompressor` 将超长工具结果压缩为固定卡片。
- `IndexedRehydrateSelector` 只在显式命中 `artifactId`、工具名或压缩摘要时回填对应 artifact。
- `TaskExecutionState` 只保留单次执行回路所需的最小任务态卡片。

### 3.2 CodeAct 最小闭环

- `AbstractAgentExecutor` 负责“计划 -> 执行工具 -> 观察结果 -> 调整计划”的单轮循环。
- 本地工具与 MCP 工具统一通过工具注册机制接入。
- 工具结果统一写回对话上下文；超长结果优先走“摘要卡片 + artifact”路径。
- OpenAI-compatible 主链路统一收口同步 / 流式空响应和上游错误语义。

### 3.3 工具结果摘要化、卸载与回填

- 超长工具结果落到 `AiToolResultArtifactStore`，对话上下文只保留索引卡片。
- 模型输入侧对超长工具结果注入 `[Tool Result Context Compressed]` 卡片。
- 回填结果统一以 `TOOL` 观察消息注入，并继续受单条上限、单轮上限和总量预算约束。

### 3.4 当前 live smoke 口径

- 默认验收只看 OpenAI-compatible 主链路。
- 默认 `test` 不直接执行外部 live smoke；仅 `./scripts/run-live-smoke.sh` 显式触发。
- `run-live-smoke.sh` 在执行前输出脱敏后的生效配置摘要，只显示模型列表、`base URL` 与 `api key` 长度/尾缀。
- OpenAI-compatible live smoke 候选按 `OPENMANUS_LIVE_* -> OPENMANUS_LLM_DEFAULT_LLM_* -> OPENAI_*` 取首个有效层级；层内合并 `MODEL + MODEL_CANDIDATES` 并去重，不跨层混合。
- 若未显式提供任何模型/候选，则回退到 `gpt-5.4,gpt-5,gpt-4o`。
- `401 unauthorized`、`invalid api key`、`无效的令牌`、`model_not_found`、`No available channel for model` 这类确定性错误不在同一候选上盲重试。

## 4. 当前验证口径

当前必须覆盖：

- 上下文组装、预算、压缩与回填主流程、分支、异常和边界。
- `AgentService` / `WorkflowStreamService` 主流程、监控收口与异常边界。
- Web 代理输入校验、重定向校验、响应头过滤与装配开关。
- 会话级沙箱编排、状态刷新、销毁与安全边界。
- `.env` / live smoke 脚本回填、候选模型解析与默认测试门禁。
- 架构守卫，确保 `domain -> port -> infra adapter` 分层不回退。

当前有效测试集合保持不变，主覆盖仍由以下测试簇承担：

- 上下文治理：`ContextSnapshotTest`、`ContextBudgetPolicyTest`、`ContextAssemblerTest`、`ToolResultContextCompressorTest`、`IndexedRehydrateSelectorTest`、`TaskExecutionStateTest`、`TaskExecutionStateTrackerTest`、`TaskStateContextInjectorTest`。
- Agent 主链路：`AbstractAgentExecutorChatMemoryIntegrationTest`、`AbstractAgentExecutorTaskStateIntegrationTest`、`AbstractAgentExecutorMcpIntegrationTest`、`AbstractAgentExecutorRuntimeEntryTest`、`UnifiedWorkflowTest`。
- OpenAI-compatible 与 live smoke 边界：`HttpTransportTest`、`SseTransportTest`、`OpenAiClientIntegrationTest`、`OpenAiResponseParserTest`、`LiveSmokeEnvTest`、`OpenAiClientLiveSmokeTestTest`、`LiveSmokeScriptIntegrationTest`、`LiveSmokeTestContractTest`。
- Domain / Infra 边界：`AgentServiceConversationMemoryTest`、`AgentServiceMonitoringIntegrationTest`、`WorkflowStreamServiceSessionMemoryTest`、`WorkflowStreamServiceMonitoringIntegrationTest`、`WebProxyControllerTest`、`WebProxyControllerConditionTest`、`WebProxyServiceTest`、`SessionSandboxManagerLifecycleTest`、`SessionSandboxManagerSecurityTest`、`SingleAgentArchitectureGuardTest`。

## 5. 本轮复验结果

- `./scripts/mvnw-local.sh -q -DskipTests compile` 已在 `2026-04-07 17:04 CST` 复验通过。
- `./scripts/mvnw-local.sh -q -DskipITs test` 已在 `2026-04-07 17:05 CST` 复验通过。
- `./scripts/run-live-smoke.sh` 已在 `2026-04-07 17:05 CST` 复验失败，当前脱敏生效配置摘要为：
  `models=gpt-5.4, baseUrl=https://ruoli.dev/v1, apiKey=len=51,suffix=nLy0`
- 当前外部失败收敛为 OpenAI-compatible 渠道鉴权错误：
  `status=401`，错误摘要为 `无效的令牌`，`request id=202604070905147409064708268d9d6eJhGVdBU`。

## 6. 当前收口判断

- 当前默认执行面仍维持在既定阶段边界内，没有扩张到阶段外能力。
- 仓内实现与测试已满足当前阶段的仓内收口要求。
- 当前唯一未满足的完成标准仍是外部 OpenAI-compatible live smoke 未转绿。
- 在外部凭证或渠道修正前，本阶段继续保持 `Blocked`，不做代码侧扩大实现。
