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

当前阶段 provider 口径：

- 默认验收只看 OpenAI-compatible 主链路。
- Anthropic / Gemini 只保留配置兼容、接入点和测试预留，不作为本阶段收口前置条件。
- OpenAI-compatible live smoke 只在“未显式提供任何模型/候选模型”时回退到内置候选：`gpt-5.4`、`gpt-5`、`gpt-4o`；一旦已提供 `OPENMANUS_LIVE_MODEL[/CANDIDATES]`、`OPENMANUS_LLM_DEFAULT_LLM_MODEL[/CANDIDATES]` 或 `OPENAI_MODEL[/CANDIDATES]`，就只试显式配置的候选，不再追加内置噪音模型。
- OpenAI-compatible `HttpTransport` 与 `SseTransport` 在 `429`、`5xx` 与 vendor-wrapped `bad_response_status_code` 分支统一做短退避后重试；live smoke 端到端校验在单模型内额外保留有限次重试，只用于吸收网关瞬时抖动，不改变主链路协议口径。

## 2. 当前有效分层

- `domain` 只保留业务语义和 port，不直接依赖运行时、监控推送、Web 抓取或框架细节。
- `agent` 负责单 Agent 执行编排、上下文治理、工具结果压缩与按需回填。
- `infra` 负责配置、存储、监控、沙箱、Web 代理和 workflow adapter。
- `aiframework` 负责模型、消息和工具运行时抽象。

当前必须守住的边界：

- `domain/service` 只通过 `WorkflowExecutionPort` 调工作流；`infra/workflow/UnifiedWorkflowPortAdapter` 负责对接 `UnifiedWorkflow`。
- `AgentService` 与 `WorkflowStreamService` 统一通过 `WorkflowExecutionEventPort` 收口 workflow tracking、execution start/end 和失败记录。
- `WorkflowStreamService` 只依赖 `WorkflowExecutionEventPort` 与 `WorkflowStreamPublisher`。
- `SessionSandboxManager` 只做会话级编排，运行时沙箱细节下沉到 `infra/sandbox`。
- `WebProxyController` 与 `WebProxyService` 只依赖 `WebProxyFetchPort`；URL 校验、请求转发、重定向复检和响应头过滤下沉到 `infra/web`。
- MCP 工具接入继续受 `openmanus.mcp.enabled=true` 保护，但当前阶段只允许“工具发现 + 工具调用协议转换”进入主链路。
- `mcp.resource.read` 不属于当前阶段默认主链路；它通过独立开关 `openmanus.mcp.resource-read-enabled` 控制，默认关闭，不再与 `openmanus.mcp.enabled` 共用同一默认入口。
- `McpToolRegistryBootstrap` 的便捷构造默认值已与运行时入口对齐；只有显式开启时才注册 `mcp.resource.read`。

## 3. 当前最小可运行链路

### 3.1 上下文治理

- `ContextSnapshot` 统一拆分历史消息、当前轮消息和当前用户输入。
- `ContextBudgetPolicy` 统一消息数与总量预算。
- `ContextAssembler` 按固定顺序组装模型输入：先历史、再当前轮、最后做总量裁剪。
- 当 `ContextSnapshot` 没有 `fullMessages` 但已经携带当前轮消息时，`ContextAssembler` 仍以当前轮消息作为最小组装输入，不丢失当前用户消息后的工具观察。
- `ToolResultContextCompressor` 对超长工具结果生成固定压缩卡片，只保留 `keyFacts`、`recentActions`、`todo`、`artifactId` 与必要预览。
- `IndexedRehydrateSelector` 只在显式 `artifactId`、工具名或压缩卡片摘要命中时选择 artifact 回填；其中压缩卡片摘要命中只允许命中对应 `artifactId`，不把同工具的其他 artifact 一起放入候选。
- `TaskExecutionState` 只保留单次执行回路所需的最小任务态卡片，不展开到阶段 C 的结构化任务系统。

### 3.2 CodeAct 最小闭环

- `AbstractAgentExecutor` 负责“计划 -> 执行工具 -> 观察结果 -> 调整计划”的单轮循环。
- 本地工具继续通过统一工具注册机制接入。
- 工具结果统一写回对话上下文；超长结果优先走“摘要卡片 + artifact”路径。
- MCP 代码只保留最小接入点；资源读取能力仅在 `openmanus.mcp.resource-read-enabled=true` 时进入验证链路，默认不进入当前阶段执行面。
- OpenAI-compatible client 对 SSE 结果统一走同一完成态校验：若只有 `usage`、没有文本、工具调用和 `finishReason`，同步与流式都按空响应失败收口。
- OpenAI-compatible live smoke 对单模型仍保留有限次重试，但 `model_not_found` / `No available channel for model` 这类确定性配置错误不再在同一候选上盲重试，直接切到下一个候选或收口失败。

### 3.3 工具结果摘要化、卸载与回填

- 超长工具结果可落到 `AiToolResultArtifactStore`，chat memory 中只保留 `[Tool Result Offloaded]` 索引卡片。
- `ContextAssembler` 在模型输入侧会把超长原始工具结果压缩成 `[Tool Result Context Compressed]` 卡片。
- 回填结果统一以 `TOOL` 观察消息注入，并继续受单条字符上限、单轮数量上限和总量预算约束。

### 3.4 监控、沙箱与代理收口

- `/api/agent/chat` 与 `/workflow-stream` 都必须产出最小 workflow tracking 和 execution 终态。
- 异常统一解包到根因，空白异常文案统一归一为 `unknown error`。
- `SessionSandboxManager` 只缓存会话级 `SessionSandboxInfo` 快照；运行态探测、停止态刷新和按 `containerId` 销毁全部下沉到 `infra/sandbox/SessionSandboxClientAdapter`。
- `SessionSandboxInfo` 只保留 `sessionId`、`vncUrl`、`createdAt` 与 `status` 等会话级快照；容器 ID、端口等运行时标识只允许保留在 `infra/sandbox` 私有句柄中。
- `SessionSandboxManager` 的注释、日志和对外表述统一收敛为“会话级沙箱编排”语义，不在 `domain` 层暴露容器/VNC 运行时实现细节。
- `/api/proxy/web` 只接受 base64url 目标地址；代理仅允许显式 `http/https` 绝对 URL，并拒绝回环、本地和链路本地地址。
- `/api/proxy/web` 与 `/api/proxy/url` 通过独立配置 `openmanus.web-proxy.enabled` 控制装配，默认关闭；只有显式开启时才暴露入口。
- Web 代理跨域访问只允许 `openmanus.web-proxy.allowed-origins` 中的显式 origin；白名单为空时不开放跨域访问，不再使用 `@CrossOrigin("*")`。

## 4. 验证口径

当前必须守住的覆盖面：

- 上下文组装、预算、压缩与回填选择。
- 压缩卡片摘要信号驱动的 indexed rehydrate 主流程、分支和边界。
- `AgentService` / `WorkflowStreamService` 主流程、分支、异常和边界。
- 工作流监控事件收口与 listener 生命周期。
- Web 代理输入校验、重定向校验和异常响应头过滤。
- Web 代理开关关闭、显式 origin 白名单开启与空白名单不开放跨域访问。
- 沙箱生命周期与状态探测边界。
- 配置回退、`.env` 回填和 live smoke 脚本一致性。
- 架构守卫，确保 `domain -> port -> infra adapter` 分层不回退。

`2026-04-06` 本轮复验结果：

- `./scripts/mvnw-local.sh -q -DskipTests compile` 通过。
- `./scripts/mvnw-local.sh -q -DskipITs test` 通过。
- `./scripts/run-live-smoke.sh` 失败；当前输出为 `tests=1, failures=1, errors=0, skipped=0`，失败项为 OpenAI-compatible 主链路 `401` `无效的令牌`。

当前已落地且仍有效的验证点：

- `ContextSnapshotTest`、`ContextBudgetPolicyTest`、`ContextAssemblerTest`、`ToolResultContextCompressorTest`、`IndexedRehydrateSelectorTest`、`AbstractAgentExecutorChatMemoryIntegrationTest` 覆盖上下文治理主流程、分支、异常和边界；其中 `ContextAssemblerTest` 额外覆盖“无 `fullMessages` 但已有当前轮消息”时不丢当前轮工具观察的边界。
- `IndexedRehydrateSelectorTest` 额外覆盖“压缩卡片摘要命中后只回填对应 artifact、不扩散到同工具其他 artifact”的边界，避免 indexed rehydrate 在同工具多 artifact 场景下误注入无关结果。
- `TaskExecutionStateTest`、`TaskExecutionStateTrackerTest`、`TaskStateContextInjectorTest` 覆盖最小任务态卡片的预算、状态迁移和注入边界。
- `HttpTransportTest`、`SseTransportTest`、`OpenAiClientIntegrationTest`、`OpenAiResponseParserTest` 覆盖 OpenAI-compatible 同步/流式路径、SSE 聚合、错误 payload、retry 分支，以及“200 + SSE error payload”不被误包装成解析失败的异常透传路径。
- `HttpTransportTest` 与 `SseTransportTest` 额外覆盖 `530` 等瞬时 `5xx` 上游错误的短退避重试分支，确保 OpenAI-compatible 主链路与 live smoke 对网关抖动的收口一致。
- `OpenAiClientIntegrationTest` 额外覆盖 OpenAI-compatible 流式 `2xx + usage-only SSE` 空响应分支，确保 `streamChat()` 不会把空结果误报为成功完成。
- `OpenAiClientLiveSmokeTestTest` 与 `LiveSmokeScriptIntegrationTest` 额外覆盖 OpenAI-compatible live smoke 在“env 未配置 -> skipped”“候选模型全部失败 -> failure”“单模型多次失败摘要”以及嵌套异常根因摘要几条收口路径，避免 provider 实际失败被误归类成 `skipped` 或被模糊失败文案掩盖。
- `OpenAiClientLiveSmokeTestTest` 额外覆盖 `model_not_found` / `No available channel for model` 的非重试分支，以及 `empty SSE`、vendor-wrapped `bad_response_status_code` 仍可继续重试的分支，保证候选模型切换只在确定性配置错误上提前收口。
- `LiveSmokeScriptIntegrationTest` 额外覆盖 `run-live-smoke.sh` 的首个 `failure/error/skipped` 明细提取、XML 转义解码和 CDATA 明细输出，保证 live smoke 阻塞可直接在终端收口。
- `OpenManusPropertiesEnvFallbackTest`、`McpRuntimeConfigWiringTest`、`Step2UnifiedAgentConfigRuntimeBehaviorTest`、`McpToolRegistryBootstrapTest` 额外覆盖 MCP resource-read 独立开关的默认关闭、显式开启、缓存边界和主链路装配分支。
- `LiveSmokeEnvTest` 与 `LiveSmokeScriptIntegrationTest` 已覆盖 `.env` 解析、default-llm fallback、legacy OpenAI fallback、候选模型回退、可选 provider 装配，以及脚本到 Maven/Surefire 进程的环境变量透传。
- `SessionSandboxManagerLifecycleTest`、`SessionSandboxManagerSecurityTest`、`SessionSandboxClientAdapterTest`、`AgentControllerSessionInfoTest` 与 `SingleAgentArchitectureGuardTest` 已覆盖沙箱会话级编排、状态刷新、销毁异常、文件沙箱委托、前端查询响应，以及 `domain` 不再暴露容器 ID/端口或回写容器/VNC 语义的边界守卫。
- `WebProxyControllerTest`、`WebProxyControllerConditionTest`、`WebProxyServiceTest`、`WebMvcConfigTest` 与 `OpenManusPropertiesEnvFallbackTest` 已覆盖 Web 代理主流程、base64url 入参校验、异常映射、条件装配、开关关闭、显式 origin 白名单、空白名单不开放跨域，以及系统属性回填边界。

## 5. 当前收口判断

- 当前工作树仍维持既定阶段边界：单 Agent、上下文治理阶段 A、CodeAct 阶段 A，以及“工具结果摘要化 / 卸载索引 / 按需回填”最小链路，没有扩展到上下文治理阶段 B 后续切片 / C、MCP 资源融合或 `Multi-Agent` 默认实现面。
- 当前阶段验收条件尚未满足：`compile + test` 已通过，但 `live smoke` 仍阻塞，当前不能按“阶段已收口”推进。
- 当前处理原则仍是守住现有 `domain / agent / infra / aiframework` 边界；后续只在环境恢复后仍出现主链路失败时，才允许在 `aiframework transport/client/parser`、`agent` 上下文治理或对应 `infra adapter` 边界做最小修正，不向 `domain`、Controller 或配置层扩散兼容逻辑。
