# 技术实现方案

## 1. 当前阶段范围

已完成以下实现面：

1. 上下文治理阶段 A。
2. CodeAct 阶段 A。
3. 上下文治理阶段 B 第一切片：工具结果摘要化、卸载索引与按需回填。
4. 文件级动态装载收尾：旧 artifact / rehydrate / snapshot 链路已清理，收敛为"工具原始结果 + API 前统一预算 + Shell 显式读取"。

当前不进入：

- 上下文治理阶段 C。
- MCP 资源融合。
- `Multi-Agent` 默认实现面。

当前阶段完成标准：

1. 单 Agent 最小链路稳定。
2. `./scripts/mvnw-local.sh -q -DskipTests compile` 通过 ✅
3. `./scripts/mvnw-local.sh -q -DskipITs test` 通过 ✅
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
- 工具注册统一由 `infra/config/AgentArchitectureConfig` 收口；当前注册工具为 `BrowserTool`、`PythonExecutionTool`、`SearchTool`、`WebFetchTool`、`ShellTool`、`TaskReflectionTool`。
- MCP 当前只保留"工具发现 + 工具调用协议转换"；`mcp.resource.read` 不进入当前阶段默认配置、装配与验收面。

## 3. 当前最小可运行链路

### 3.1 上下文治理

- `ContextSnapshot` 拆分历史消息、当前轮消息和当前用户输入。
- `ContextAssembler` 负责把完整历史消息、当前用户输入与任务态卡片组装成模型请求。
- 当前不再做本地消息窗口裁剪、历史摘要或 token 预算截断。
- `TaskExecutionState` 只保留单次执行回路所需的最小任务态卡片。

### 3.2 CodeAct 最小闭环

- `AbstractAgentExecutor` 负责"计划 -> 执行工具 -> 观察结果 -> 调整计划"的单轮循环。
- 本地工具与 MCP 工具统一通过工具注册机制接入。
- `McpToolRegistryBootstrap` 当前只发现并注册 MCP tools，不再在阶段内额外合成 `mcp.resource.read` 工具。
- 工具结果统一写回对话上下文；超长结果在下一轮模型请求前由 `ToolResultBudget` 统一落盘并替换为 stub。
- 容器内大文本不再通过 shell 参数内联传输：文件写入走 Docker archive copy，Python 脚本执行走 `stdin` + `python3 -`。
- OpenAI-compatible 主链路统一收口同步 / 流式空响应和上游错误语义。

### 3.3 工具结果预算、卸载与显式读取

- 超长工具结果由 `ToolResultBudget` 写入 `.openmanus/tool-results/`。
- 模型输入侧把原始 `TOOL` 消息替换为 `[Tool Result Stub]`，明确提示路径与推荐读取方式。
- 如模型需要完整内容，应显式调用 Shell 工具读取，而不是依赖本地历史摘要回填。

### 3.4 Live Smoke 口径

- 默认验收只看 OpenAI-compatible 主链路。
- 默认 `test` 不直接执行外部 live smoke；仅 `./scripts/run-live-smoke.sh` 显式触发。
- `./scripts/mvnw-local.sh` 在执行 `test` 前清理历史 `target/surefire-reports`，确保默认测试证据不继承旧的 live smoke 失败产物。
- 应用运行时默认 LLM 只接受 `OPENMANUS_LLM_DEFAULT_LLM_*` 与兼容 `OPENAI_*` 回填；`OPENMANUS_LIVE_*` 只作为 `run-live-smoke.sh` 的验收输入，不进入应用默认主链路。
- 应用内环境回填统一按 `JVM system properties -> process env` 取首个非空值，确保 `.env`/测试注入的系统属性优先于宿主机环境变量。
- live smoke 显式触发使用独立系统属性 `-Dopenmanus.liveSmoke.enabled=true` 作为二次门禁。
- 当 shell 中未显式导出 `OPENMANUS_LIVE_*` / `OPENMANUS_LLM_DEFAULT_LLM_*` / `OPENAI_*` 时，`run-live-smoke.sh` 先加载 `.env`，再按 `OPENMANUS_LIVE_* -> OPENMANUS_LLM_DEFAULT_LLM_* -> OPENAI_*` 回填生效配置。
- `run-live-smoke.sh` 执行前输出脱敏后的生效配置摘要，只显示模型列表、`base URL` 与 `api key` 长度/尾缀。
- OpenAI-compatible live smoke 候选按 `OPENMANUS_LIVE_* -> OPENMANUS_LLM_DEFAULT_LLM_* -> OPENAI_*` 取首个有效层级；层内合并 `MODEL + MODEL_CANDIDATES` 并去重，不跨层混合。
- 若未显式提供任何模型/候选，则回退到 `gpt-5.4,gpt-5,gpt-4o`。
- 当网关 TLS 证书链不在当前 JVM 默认信任集中时，可显式提供 `OPENMANUS_LIVE_CA_CERT_FILE` 指向 PEM 证书链。
- 外部网关、证书链或凭证不满足时，live smoke 直接按跳过处理，不影响当前阶段持续推进。
- `401 unauthorized`、`402 insufficient_quota/insufficient_balance`、`invalid api key`、`无效的令牌`、`model_not_found`、`No available channel for model` 这类确定性错误不在同一候选上盲重试。

## 4. 当前验证口径

当前必须覆盖：

- 上下文组装、任务态注入、工具结果预算与协议校验主流程、分支、异常和边界。
- `AgentService` / `WorkflowStreamService` 主流程、监控收口与异常边界。
- `AgentController` 统一流式入口的成功路径、状态码映射，以及"成功返回但缺失 `sessionId`"的协议异常边界。
- Web 代理输入校验、重定向校验、响应头过滤与装配开关。
- 会话级沙箱编排、状态刷新、销毁与安全边界。
- `.env` / live smoke 脚本回填、候选模型解析与默认测试门禁。
- 架构守卫，确保 `domain -> port -> infra adapter` 分层不回退，并覆盖 `agent` 层不得混入 Spring 装配语义。

当前有效测试集合：

- 上下文治理：`ContextAssemblyTest`、`ToolResultBudgetTest`
- Agent 主链路：`AgentCoordinatorSmokeTest`、`AgentToolResultBudgetE2ETest`
- 工具 smoke：`BrowserToolSmokeTest`、`PythonExecutionToolSmokeTest`、`SearchToolSmokeTest`、`ShellToolSmokeTest`、`WebFetchToolSmokeTest`、`TaskReflectionToolSmokeTest`
- Live smoke 边界：`OpenAiClientLiveSmokeTest`、`AnthropicClientLiveSmokeTest`、`GeminiClientLiveSmokeTest`、`LiveSmokeTest`
- Domain / Infra 边界：`ConversationApplicationServiceTest`、`ExecutionStreamingApplicationServiceTest`、`InMemorySessionExecutionGuardTest`、`AgentControllerSessionSandboxStartTest`、`FrontendProxyControllerTest`、`WebProxyControllerTest`、`WebSocketExecutionStreamPublisherTest`
- 沙箱：`SandboxClientContentTransportTest`、`SandboxClientDockerIntegrationTest`、`SandboxClientFailFastTest`
- 配置与属性：`OpenManusPropertiesChatMemoryContractTest`、`OpenManusPropertiesSandboxFallbackTest`、`MdcInterceptorUserIdFallbackTest`
- E2E：`AgentChatApiE2ETest`、`ExecutionStreamApiE2ETest`、`SessionApiE2ETest`

## 5. 当前收口判断

- 当前主链路编译、默认测试、分层与守卫测试维持在既定阶段目标内。
- `agent` / `infra` 分层已按当前阶段目标收口；`agent` 层不再承载 Spring 装配语义，且已有源码级守卫防回退。
- 控制器测试目录已收口到 `src/test/java/com/openmanus/infra/web/`，测试目录层级与主代码 Web 入口归属保持一致。
- 应用默认运行时与 live smoke 脚本配置边界已收口，`OPENMANUS_LIVE_*` 不再渗透进应用默认主链路。
- 文件级动态装载收尾已完成：`FileReadTool`、`ContextBudgetPolicy`、`HistoricalContextSummarizer`、`ModelContextTokenCounter`、artifact store、indexed rehydrate 已从代码中清理删除；大工具结果统一由 `ToolResultBudget` 在 executor 调模型前处理。
- 当前剩余验收缺口只在外部 live smoke non-skipped 成功证据；当前首个有效阻塞是外部 TLS 证书链问题，需先消除此入口问题，再判断是否出现新的鉴权或余额/配额类阻塞。