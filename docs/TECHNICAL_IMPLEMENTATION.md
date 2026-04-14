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
4. `./scripts/run-live-smoke.sh` 在环境满足时产出 non-skipped 成功结果；环境不满足时允许跳过，不影响阶段持续推进。

## 2. 当前有效分层

- `domain` 只保留业务语义与 port。
- `agent` 负责单 Agent 执行编排、上下文治理、CodeAct 最小闭环。
- `infra` 负责配置、存储、监控、沙箱、Web 入口与 adapter。
- `aiframework` 负责模型、消息、工具与 provider 运行时抽象。

当前必须守住的边界：

- `domain` 不承载 Spring Web / Servlet / 调度 / 条件装配语义。
- `domain/service` 只通过 port 调工作流、监控、代理与沙箱能力。
- `agent` 只保留执行编排、上下文治理、工具逻辑与最小任务态，不直接承载 Spring 装配语义。
- `infra` 负责 `Controller`、配置装配、外部协议适配和运行时细节。
- `UnifiedWorkflow`、`BrowserTool`、`FileTool`、`PythonTool`、`ReflectionTool`、`SandboxPathResolver` 作为纯执行对象保留在 `agent` 层，实例化与 Bean 注册统一由 `infra/config/UnifiedAgentConfig` 收口。
- MCP 当前只保留“工具发现 + 工具调用协议转换”；`mcp.resource.read` 不进入当前阶段默认配置、装配与验收面。

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
- `McpToolRegistryBootstrap` 当前只发现并注册 MCP tools，不再在阶段内额外合成 `mcp.resource.read` 工具。
- 工具结果统一写回对话上下文；超长结果优先走“摘要卡片 + artifact”路径。
- OpenAI-compatible 主链路统一收口同步 / 流式空响应和上游错误语义。

### 3.3 工具结果摘要化、卸载与回填

- 超长工具结果落到 `AiToolResultArtifactStore`，对话上下文只保留索引卡片。
- 模型输入侧对超长工具结果注入 `[Tool Result Context Compressed]` 卡片。
- 回填结果统一以 `TOOL` 观察消息注入，并继续受单条上限、单轮上限和总量预算约束。

### 3.4 Live Smoke 口径

- 默认验收只看 OpenAI-compatible 主链路。
- 默认 `test` 不直接执行外部 live smoke；仅 `./scripts/run-live-smoke.sh` 显式触发。
- `./scripts/mvnw-local.sh` 在执行 `test` 前清理历史 `target/surefire-reports`，确保默认测试证据不继承旧的 live smoke 失败产物。
- 应用运行时默认 LLM 只接受 `OPENMANUS_LLM_DEFAULT_LLM_*` 与兼容 `OPENAI_*` 回填；`OPENMANUS_LIVE_*` 只作为 `run-live-smoke.sh` 的验收输入，不进入应用默认主链路。
- 应用内环境回填统一按 `JVM system properties -> process env` 取首个非空值，确保 `.env`/测试注入的系统属性优先于宿主机环境变量，且不改变 `OPENMANUS_LIVE_*` 与默认运行时的边界。
- live smoke 显式触发使用独立系统属性 `-Dopenmanus.liveSmoke.enabled=true` 作为二次门禁，避免与 Surefire `groups/excludedGroups` 机制相互干扰。
- 当 shell 中未显式导出 `OPENMANUS_LIVE_*` / `OPENMANUS_LLM_DEFAULT_LLM_*` / `OPENAI_*` 时，`run-live-smoke.sh` 先加载 `.env`，再按 `OPENMANUS_LIVE_* -> OPENMANUS_LLM_DEFAULT_LLM_* -> OPENAI_*` 回填生效配置。
- `run-live-smoke.sh` 执行前输出脱敏后的生效配置摘要，只显示模型列表、`base URL` 与 `api key` 长度/尾缀。
- OpenAI-compatible live smoke 候选按 `OPENMANUS_LIVE_* -> OPENMANUS_LLM_DEFAULT_LLM_* -> OPENAI_*` 取首个有效层级；层内合并 `MODEL + MODEL_CANDIDATES` 并去重，不跨层混合。
- 若未显式提供任何模型/候选，则回退到 `gpt-5.4,gpt-5,gpt-4o`。
- 当网关 TLS 证书链不在当前 JVM 默认信任集中时，可显式提供 `OPENMANUS_LIVE_CA_CERT_FILE` 指向 PEM 证书链；脚本会导入临时 `PKCS12 truststore` 并通过 `JAVA_TOOL_OPTIONS` 注入给 live smoke 进程，不放宽 TLS 校验。
- 外部网关、证书链或凭证不满足时，live smoke 直接按跳过处理，不影响当前阶段持续推进。
- `401 unauthorized`、`402 insufficient_quota/insufficient_balance`、`invalid api key`、`无效的令牌`、`model_not_found`、`No available channel for model` 这类确定性错误不在同一候选上盲重试。
- 其中 `401/402/invalid api key/invalid token/insufficient_balance` 视为候选无关的外部凭证或配额失败，首个候选命中后直接停止剩余候选探测；`model_not_found`、`No available channel for model` 仍只终止当前候选，允许切到下一个候选继续验证。

## 4. 当前验证口径

当前必须覆盖：

- 上下文组装、预算、压缩与回填主流程、分支、异常和边界。
- `AgentService` / `WorkflowStreamService` 主流程、监控收口与异常边界。
- `AgentController` 统一流式入口的成功路径、状态码映射，以及“成功返回但缺失 `sessionId`”的协议异常边界。
- Web 代理输入校验、重定向校验、响应头过滤与装配开关。
- 会话级沙箱编排、状态刷新、销毁与安全边界。
- 会话级沙箱状态刷新失败时保留缓存快照，避免探测异常打断查询链路。
- `.env` / live smoke 脚本回填、候选模型解析与默认测试门禁。
- `./scripts/mvnw-local.sh` 对 Java 21 运行前置校验、降级 `JAVA_HOME` 回退和 surefire 清理门禁。
- 架构守卫，确保 `domain -> port -> infra adapter` 分层不回退，并覆盖 `agent` 层不得混入 Spring 装配语义。
- MCP 当前阶段只验证工具发现、注册、调用与重名冲突；不再把 `mcp.resource.read` 作为阶段内默认回归项。

当前有效测试集合主覆盖由以下测试簇承担：

- 上下文治理：`ContextSnapshotTest`、`ContextBudgetPolicyTest`、`ContextAssemblerTest`、`ToolResultContextCompressorTest`、`IndexedRehydrateSelectorTest`、`TaskExecutionStateTest`、`TaskExecutionStateTrackerTest`、`TaskStateContextInjectorTest`。
- Agent 主链路：`AbstractAgentExecutorChatMemoryIntegrationTest`、`AbstractAgentExecutorTaskStateIntegrationTest`、`AbstractAgentExecutorMcpIntegrationTest`、`AbstractAgentExecutorRuntimeEntryTest`、`UnifiedWorkflowTest`。
- OpenAI-compatible 与 live smoke 边界：`HttpTransportTest`、`SseTransportTest`、`OpenAiResponseParserTest`、`LiveSmokeEnvTest`、`OpenAiClientLiveSmokeTestTest`、`LiveSmokeScriptIntegrationTest`、`LiveSmokeTestContractTest`。
- Domain / Infra 边界：`AgentServiceConversationMemoryTest`、`AgentServiceMonitoringIntegrationTest`、`WorkflowStreamServiceSessionMemoryTest`、`WorkflowStreamServiceMonitoringIntegrationTest`、`AgentControllerStreamEndpointTest`、`AgentControllerServiceContractTest`、`AgentControllerSessionInfoTest`、`WebProxyControllerTest`、`WebProxyControllerConditionTest`、`WebProxyServiceTest`、`SessionSandboxManagerLifecycleTest`、`SessionSandboxManagerSecurityTest`、`SingleAgentArchitectureGuardTest`。
- 装配与边界守卫：`UnifiedAgentConfigBeanWiringTest`、`UnifiedAgentConfigRuntimeEntryDelegationTest`、`Step2UnifiedAgentConfigRuntimeBehaviorTest`、`McpRuntimeConfigWiringTest`、`SingleAgentArchitectureGuardTest`。

## 5. 当前复验结果

- 截至 `2026-04-14`，`./scripts/mvnw-local.sh -q -DskipTests compile` 与 `./scripts/mvnw-local.sh -q -DskipITs test` 已通过；仓内默认验证入口继续满足当前阶段要求。
- 截至 `2026-04-14`，任务态上下文分支已补齐当前阶段边界覆盖：`TaskExecutionStateTrackerTest` 新增“缺失 assistant 计划消息”、“空白 assistant 计划不清空既有 plan”、“超短失败预算裁剪”分支，`TaskStateContextInjectorTest` 新增“缺失 baseMessages”与“任务态字段兜底为 `n/a`”边界。
- 截至 `2026-04-14`，`OpenManusProperties` 默认回填仍保持“系统属性优先、宿主环境兜底”，默认运行时不再受宿主机 `OPENAI_*` 环境污染，`OPENMANUS_LIVE_*` 仍不进入应用默认主链路。
- 截至 `2026-04-14`，`./scripts/run-live-smoke.sh` 最新实测结果为 `tests=1, failures=0, errors=0, skipped=1`；当前首个有效阻塞已更新为外部兼容网关握手阶段失败 `Remote host terminated the handshake`。
- 在握手问题消除前，不继续假设 live smoke 一定会前移到证书链、鉴权或余额/配额问题；外部验收结论始终以最新一次实测结果为准。
- 当前脚本与回归测试仍只负责保证 live smoke 的候选模型收敛、环境回填、显式门禁与确定性错误停止策略，不因外部环境未闭环而扩写阶段内实现面。

## 6. 当前收口判断

- 当前主链路编译、默认测试、分层与守卫测试维持在既定阶段目标内，`mcp.resource.read` 已进一步退出当前阶段默认工具注册与测试维护面，阶段边界重新与 `AGENTS.md` 和开发目标对齐。
- `agent` / `infra` 分层已按当前阶段目标收口；`agent` 层不再承载 Spring 装配语义，且已有源码级守卫防回退。
- 控制器测试目录已收口到 `src/test/java/com/openmanus/infra/web/`，测试目录层级与主代码 Web 入口归属保持一致。
- 应用默认运行时与 live smoke 脚本配置边界已收口，`OPENMANUS_LIVE_*` 不再渗透进应用默认主链路。
- 默认 `compile` / `test` 入口已同时具备 Java 21 版本自检与历史 surefire 报告清理，默认复验证据与外部 live smoke 证据边界清晰。
- 当前剩余验收缺口只在外部 live smoke non-skipped 成功证据；当前首个有效阻塞是远端握手终止，需先消除此入口问题，再判断是否出现新的证书链、鉴权或余额/配额类阻塞。
