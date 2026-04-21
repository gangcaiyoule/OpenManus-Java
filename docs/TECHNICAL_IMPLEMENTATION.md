# 技术实现方案

## 1. 当前阶段范围

当前阶段已完成以下实现面：

1. 上下文治理阶段 A。
2. CodeAct 阶段 A。
3. 上下文治理阶段 B 第一切片：工具结果摘要化、卸载索引与按需回填。
4. 上下文治理阶段 B 第二切片：历史关键记忆卡片。
5. MCP 工具发现、注册与调用桥接。

当前不进入：

- 上下文治理阶段 B 后续切片中的"结构化重要事实提炼"与阶段 C。
- MCP 资源融合。
- `Multi-Agent` 默认实现面。

当前阶段下一步：

- 代码结构精简（以 `agent/context` 包为主，消除职责重叠和代码重复）。

当前阶段完成标准：

1. 单 Agent 最小链路稳定。
2. `./scripts/mvnw-local.sh -q -DskipTests compile` 通过。
3. `./scripts/mvnw-local.sh -q -DskipITs test` 通过。
4. `./scripts/run-live-smoke.sh` 在环境满足时产出 non-skipped 成功结果；环境不满足时允许 assumption skip，但必须明确首个真实外部阻塞。
5. 代码结构精简不引入任何运行时行为变化。

## 2. 当前有效分层

- `domain`：业务语义与 port。
- `agent`：单 Agent 执行编排、上下文治理、CodeAct 最小闭环。
- `infra`：配置装配、存储、监控、沙箱、Web 入口与 adapter。
- `aiframework`：模型、消息、工具与 provider 运行时抽象。

当前必须守住的边界：

- `domain` 不承载 Spring Web、Servlet、条件装配或运行时协议细节。
- `domain` 通过 `WorkflowExecutionPort`、`WorkflowExecutionEventPort`、`WorkflowStreamPublisher`、`WebProxyFetchPort`、`SessionSandboxClient` 等 port 表达依赖，不直接引用具体运行时实现。
- `agent` 不承载 Spring Bean 装配，只保留执行编排、工具逻辑和最小任务态。
- `infra` 负责 Controller、配置、外部协议适配与运行时实现细节；`DomainServiceConfig` 统一装配 domain service，`infra/web`、`infra/workflow`、`infra/sandbox`、`infra/monitoring` 提供对应 adapter。
- MCP 当前只覆盖"工具发现 + 工具调用协议转换"，`mcp.resource.read` 不进入当前阶段默认实现和验收面。
- `X-Session-ID` 的合法性与规范化统一由 `domain.service.SessionIdPolicy` 收口。
- `/api/agent/chat` 的 `conversationId` 与 `/api/agent/session/{sessionId}` 的会话查询参数都必须复用 `SessionIdPolicy`。

## 3. 代码文件结构

### 3.1 总体目录结构

```
src/main/java/com/openmanus/
├── StartupBanner.java                      启动横幅
├── WebApplication.java                     Spring Boot 入口
│
├── agent/                                  ← Agent 执行层
│   ├── base/
│   │   ├── AbstractAgent.java              (73L)   Agent 基类
│   │   └── AbstractAgentExecutor.java      (1236L) CodeAct 循环主引擎
│   ├── context/                            (16 文件, 2426L) 上下文治理
│   ├── impl/unified/
│   │   └── UnifiedAgent.java               (98L)   单 Agent 实现
│   ├── tool/
│   │   ├── BrowserTool.java                浏览器工具
│   │   ├── FileTool.java                   文件工具
│   │   ├── PythonTool.java                 Python 执行工具
│   │   ├── ReflectionTool.java             反射工具
│   │   └── support/
│   │       └── SandboxPathResolver.java    沙箱路径解析
│   └── workflow/
│       └── UnifiedWorkflow.java            (47L)   工作流
│
├── aiframework/                            ← AI 框架抽象层
│   ├── api/
│   │   ├── AiProviderClient.java           Provider 客户端接口
│   │   └── StreamListener.java             流式监听接口
│   ├── assembler/
│   │   ├── ProviderRequestAssembler.java   请求组装接口
│   │   ├── AbstractProviderRequestAssembler.java  组装基类
│   │   ├── OpenAiRequestAssembler.java     OpenAI 请求组装
│   │   ├── AnthropicRequestAssembler.java  Anthropic 请求组装
│   │   └── GeminiRequestAssembler.java     Gemini 请求组装
│   ├── client/
│   │   ├── AbstractAiProviderClient.java   客户端基类
│   │   ├── OpenAiClient.java              OpenAI 客户端
│   │   ├── AnthropicClient.java           Anthropic 客户端
│   │   └── GeminiClient.java              Gemini 客户端
│   ├── config/
│   │   └── AiProviderClientRegistry.java   Provider 注册表
│   ├── exception/
│   │   └── AiFrameworkException.java       框架异常
│   ├── model/
│   │   ├── AiProviderType.java             Provider 类型枚举
│   │   ├── ChatMessage.java, ChatRequestEnvelope.java
│   │   ├── ChatResponseEnvelope.java, ChatRequestOptions.java
│   │   ├── ProviderConfig.java, ProviderStreamChunk.java
│   ├── parser/
│   │   ├── ProviderResponseParser.java     响应解析接口
│   │   ├── OpenAiResponseParser.java       OpenAI 响应解析
│   │   ├── AnthropicResponseParser.java    Anthropic 响应解析
│   │   └── GeminiResponseParser.java       Gemini 响应解析
│   ├── runtime/
│   │   ├── AiChatModel.java               Chat 模型抽象
│   │   ├── AiProviderChatModel.java        Provider Chat 实现
│   │   ├── AiMemory.java / AiMemoryProvider.java / AiMemoryStore.java
│   │   ├── AiSystemMessageMemory.java
│   │   ├── AiToolResultArtifactStore.java
│   │   ├── AiExecutionEvent.java / AiExecutionStatus.java / AiExecutionTracker.java
│   │   ├── AiCodeSandbox.java / AiCodeExecutionResult.java
│   │   ├── AiProxyConfig.java / AiSearchConfig.java
│   │   ├── AiSessionSandboxGateway.java / AiSessionSandboxInfo.java
│   │   ├── AiVncSandboxClient.java / AiVncSandboxInfo.java
│   │   ├── AiLegacyMappingPolicy.java / AiLogMarkers.java
│   │   ├── ToolResultArtifactRef.java
│   │   ├── model/                          AiChatMessage, AiChatRequest, AiChatResponse, AiToolCall 等
│   │   └── mcp/                            McpClient, McpTool*, StubMcpClient
│   ├── tool/
│   │   ├── AiTool.java, AiParam.java       工具注解
│   │   ├── AiRegisteredTool.java           已注册工具
│   │   ├── AiToolRegistry.java             工具注册表
│   │   ├── AiToolExecutor.java             工具执行器
│   │   ├── AiToolExecutionRequest.java     工具执行请求
│   │   └── mcp/
│   │       ├── McpToolExecutorBridge.java  MCP 工具执行桥
│   │       ├── McpToolRegistryBootstrap.java  MCP 工具注册引导
│   │       └── McpToolSpecAdapter.java     MCP 工具规格适配
│   └── transport/
│       ├── HttpTransport.java              HTTP 传输
│       └── SseTransport.java              SSE 传输
│
├── domain/                                 ← 业务语义层
│   ├── model/
│   │   ├── AgentExecutionEvent.java        执行事件
│   │   ├── DetailedExecutionFlow.java      详细执行流
│   │   ├── SessionSandboxInfo.java         沙箱信息
│   │   ├── WorkflowErrorCodes.java         错误码
│   │   ├── WorkflowRequest.java / WorkflowResponse.java
│   │   └── WorkflowResultVO.java
│   └── service/
│       ├── AgentService.java               Agent 服务编排
│       ├── WorkflowStreamService.java      流式工作流服务
│       ├── ExecutionMonitoringService.java 执行监控
│       ├── SessionSandboxManager.java      会话沙箱管理
│       ├── SessionIdPolicy.java            会话 ID 策略
│       ├── SessionFileSandboxDirectoryProvider.java
│       ├── SessionSandboxClient.java       沙箱客户端 port
│       ├── LegacySessionMappingPolicy.java
│       ├── WebProxyService.java            Web 代理服务
│       ├── WebProxyConfigProvider.java     代理配置 port
│       ├── WebProxyFetchPort.java          代理获取 port
│       ├── WebProxyResult.java             代理结果
│       ├── WorkflowExecutionPort.java      工作流执行 port
│       ├── WorkflowExecutionEventPort.java 工作流事件 port
│       └── WorkflowStreamPublisher.java    流式发布 port
│
└── infra/                                  ← 基础设施层
    ├── config/                             (14 文件)
    │   ├── AiFrameworkConfig.java          AI 框架配置
    │   ├── AiRuntimeConfig.java            AI 运行时配置
    │   ├── DomainServiceConfig.java        Domain 服务装配
    │   ├── UnifiedAgentConfig.java         统一 Agent 配置
    │   ├── OpenManusProperties.java        应用属性
    │   ├── DotenvLoader.java               .env 加载器
    │   ├── McpRuntimeConfig.java           MCP 运行时配置
    │   ├── AsyncConfig.java / JacksonConfig.java
    │   ├── WebMvcConfig.java / WebSocketConfig.java
    │   ├── MdcInterceptor.java             MDC 拦截器
    │   ├── RuntimeBrowserConfigAdapter.java
    │   └── RuntimeLegacyMappingPolicyAdapter.java
    ├── exception/
    │   ├── OpenManusException.java
    │   ├── TokenLimitExceededException.java
    │   └── ToolErrorException.java
    ├── log/
    │   ├── LogMarkers.java / LogRelayBridge.java / LogRelayService.java
    │   └── WebSocketLogAppender.java
    ├── memory/
    │   ├── InMemoryAiMemoryStore.java      内存记忆存储
    │   ├── PersistentAiMemory.java         持久化记忆
    │   ├── FileChatMemoryStore.java        文件对话记忆
    │   ├── AtomicAppendChatMemoryStore.java
    │   ├── InMemoryToolResultArtifactStore.java
    │   ├── FileToolResultArtifactStore.java
    │   ├── RuntimeToolResultArtifactStoreAdapter.java
    │   └── ToolResultArtifactStore.java
    ├── monitoring/
    │   ├── AgentExecutionTracker.java
    │   ├── ExecutionMonitoringServiceAdapter.java
    │   ├── RuntimeExecutionTrackerAdapter.java
    │   ├── WebSocketWorkflowStreamPublisher.java
    │   └── WorkflowExecutionEventPortAdapter.java
    ├── sandbox/
    │   ├── DockerClientManager.java / SandboxClient.java
    │   ├── RuntimeCodeSandboxAdapter.java
    │   ├── RuntimeSessionSandboxGatewayAdapter.java
    │   ├── RuntimeVncSandboxClientAdapter.java / VncSandboxClient.java / VncSandboxInfo.java
    │   ├── SessionFileSandboxDirectoryProviderAdapter.java
    │   ├── SessionSandboxClientAdapter.java / SessionSandboxLifecycleManager.java
    │   └── ExecutionResult.java
    ├── web/
    │   ├── AgentController.java             Agent 控制器 (chat/stream/session)
    │   ├── AgentMonitoringController.java   监控控制器
    │   ├── WebProxyController.java          Web 代理控制器
    │   ├── HttpUrlConnectionWebProxyAdapter.java
    │   ├── WebProxyTargetValidator.java     代理目标校验
    │   ├── WebSocketHeartbeatController.java
    │   └── WorkflowStreamResponse.java
    └── workflow/
        └── UnifiedWorkflowPortAdapter.java  工作流 port 适配器
```

### 3.2 上下文治理文件详细结构 (`agent/context/`)

共 16 个源文件，2426 行，按职责分为 4 组：

**核心管道 (4 文件, 571L)**

| 文件 | 行数 | 职责 |
|------|------|------|
| `ContextSnapshot` | 64L | 不可变 record，将消息拆分为历史/当前轮视图 |
| `ContextAssembler` | 129L | 组装编排：历史→当前轮→预算裁剪→压缩→注入任务态 |
| `ContextBudgetPolicy` | 191L | 消息数量预算，enforce history/total 限制 |
| `ModelContextBudgeter` | 187L | Token 预算选择器，保系统/用户/工具锚点，再填最新消息 |

**Token 计数 (6 文件, 482L)**

| 文件 | 行数 | 职责 |
|------|------|------|
| `ModelContextTokenCounter` | 24L | Token 计数接口 |
| `ApproxModelContextTokenCounter` | 44L | 近似计数 (char/4) |
| `TokenizerModelContextTokenCounter` | 115L | Unicode 感知轻量计数（word-piece 估算） |
| `ModelTokenizerModelContextTokenCounter` | 94L | jtokkit 真实分词计数 |
| `ModelContextTokenCounterFactory` | 122L | 工厂：模式选择 + 三级 fallback |
| `ModelTokenizerEncodingMapper` | 83L | 模型名→tokenizer 编码映射 |

**任务态 (4 文件, 447L)**

| 文件 | 行数 | 职责 |
|------|------|------|
| `TaskExecutionState` | 167L | 不可变载体：plan/inProgress/todo/lastFailure |
| `TaskExecutionStateTracker` | 143L | 从助手消息和工具结果追踪状态变迁 |
| `TaskStateBudgetPolicy` | 65L | 任务态字段预算限制（5 个常量） |
| `TaskStateContextInjector` | 72L | 渲染 [Task State] 卡片注入上下文 |

**压缩/回填 (3 文件, 926L)**

| 文件 | 行数 | 职责 |
|------|------|------|
| `ToolResultContextCompressor` | 315L | 超长工具结果→摘要卡片 + artifact 索引 |
| `IndexedRehydrateSelector` | 388L | 按需回填 artifact（中英文命中策略） |
| `HistoricalContextSummarizer` | 223L | 被裁历史→[Historical Key Memory] 卡片 |

## 4. 当前最小可运行链路

### 4.1 上下文治理

- `ContextSnapshot` 拆分历史消息、当前轮消息和当前用户输入。
- `ContextBudgetPolicy` 与 `ModelContextBudgeter` 统一消息数量、总量与工具结果预算。
- `ContextAssembler` 按"历史 -> 当前轮 -> 预算裁剪"组装模型输入，并在缺失用户锚点时补回当前用户消息。
- `HistoricalContextSummarizer` 会把因 history budget 被裁掉的旧历史压缩成一张 `[Historical Key Memory]` 卡片，并插入保留的短期窗口前部，形成"短期窗口 + 关键记忆"混合上下文。
- `ToolResultContextCompressor` 将超长工具结果压缩为摘要卡片。
- `IndexedRehydrateSelector` 只在显式命中 `artifactId`、工具名或摘要信号时回填 artifact。
- `TaskExecutionState`、`TaskExecutionStateTracker`、`TaskStateContextInjector` 只保留单次执行回路需要的最小任务态。

### 4.2 CodeAct 最小闭环

- `AbstractAgentExecutor` 负责"计划 -> 执行工具 -> 观察结果 -> 调整计划"的最小循环。
- 本地工具与 MCP 工具统一通过工具注册机制接入。
- 工具结果统一写回对话上下文；超长结果优先走"摘要卡片 + artifact"路径。
- OpenAI-compatible 主链路统一收口同步、流式空响应和上游错误语义。
- `UnifiedWorkflowPortAdapter` 负责把 `agent` 执行链路适配到 `domain` 的 `WorkflowExecutionPort`，`AgentService` 与 `WorkflowStreamService` 只编排执行、监控与流式发布，不直接依赖具体 Agent 实现。

### 4.3 工具结果摘要化、卸载与回填

- 超长工具结果写入 `AiToolResultArtifactStore`，对话上下文只保留索引卡片。
- 模型输入侧只注入压缩后的 `[Tool Result Context Compressed]` 卡片。
- 压缩卡片摘要命中规则保持"中文按原文片段命中，英文按 token 边界命中"，避免 `port -> report` 这类英文子串误触发 artifact 回填。
- 回填内容统一以 `TOOL` 观察消息注入，并继续受单条、单轮和总量预算约束。

### 4.4 Live Smoke 口径

- 默认验收只看 OpenAI-compatible 主链路。
- 默认 `test` 不执行 live smoke；仅 `./scripts/run-live-smoke.sh` 显式触发。
- live smoke 生效配置按 `OPENMANUS_LIVE_* -> OPENMANUS_LLM_DEFAULT_LLM_* -> OPENAI_*` 收敛，且不回灌到应用默认运行时。
- 当 TLS 证书链不在 JVM 默认信任集中时，可通过 `OPENMANUS_LIVE_CA_CERT_FILE` 为 live smoke 进程注入临时 truststore。
- 外部网关、证书链、余额额度或凭证环境不满足时，live smoke 允许 skip，不把问题回推成应用默认主链路改造。

## 5. 代码结构精简计划

### 5.1 背景

`agent/context` 包在阶段 B 两个切片演进过程中自然积累了 16 个源文件。在进入阶段 C 或更多功能前，应精简代码结构以保持可维护性。

### 5.2 精简范围与优先级

**P0 — Token 计数层精简 (6→3 文件)**

| 操作 | 说明 | 预期减少 |
|------|------|----------|
| 合并 `TokenizerModelContextTokenCounter` → `ApproxModelContextTokenCounter` | 两者都是估算实现，合并为统一的估算公式 | -115L, -1 文件 |
| `ModelTokenizerEncodingMapper` 内联到 `ModelTokenizerModelContextTokenCounter` | 唯一调用方就是它 | -83L, -1 文件 |

**P1 — 消除重复代码**

| 操作 | 说明 | 预期减少 |
|------|------|----------|
| `ContextBudgetPolicy` + `ModelContextBudgeter` 提取共享工具方法 | `findMessageIndex`, `findLatestToolResultMessage`, `tail`, `sanitize` 约 60L 重复 | -60L |
| `TaskStateBudgetPolicy` 合并到 `TaskExecutionState` | 本质只是 5 个常量 + getter | -65L, -1 文件 |

**P2 — 构造器精简**

| 操作 | 说明 | 预期减少 |
|------|------|----------|
| `ContextAssembler` 只保留全参构造函数 | Spring 管理不需要多个构造函数 | -20L |

### 5.3 精简约束

- 不改变任何运行时行为和外部可观测语义。
- 每步精简后执行完整测试套件 (`mvnw-local.sh -q -DskipITs test`) 确认无回归。
- 精简过程中如果发现需要调整测试，一并调整。
- 精简完成后重新执行完整验收口径。

## 6. 当前验证口径

当前必须覆盖：

- 上下文组装、预算、历史关键记忆卡片、压缩、artifact 索引与按需回填的主流程、分支、异常和边界。
- `AgentService`、`WorkflowStreamService` 的主流程、异步异常边界和监控收口。
- `AgentController` 统一流式入口、状态码映射、协议异常边界，以及 `/api/agent/chat` 在输入校验失败时只回显合法 `conversationId` 的安全边界。
- `AgentController` 的会话查询入口必须覆盖合法会话 ID、空白规范化、非法字符拒绝和"不命中沙箱信息时仅返回会话 ID"的边界。
- Web 代理输入校验、上游错误脱敏、响应头过滤与装配开关。
- 监控控制器非法参数、脏数据与查询失败分支。
- 会话级沙箱编排、状态刷新、销毁与安全边界。
- `.env` 与 live smoke 脚本的配置回填、候选模型解析与默认测试门禁。
- 架构守卫，确保 `domain`、`agent`、`infra`、`aiframework` 分层不回退。
- MCP 当前阶段只验证工具发现、注册、调用与重名冲突。

当前默认复验入口：

- `./scripts/mvnw-local.sh -q -DskipTests compile`
- `./scripts/mvnw-local.sh -q -DskipITs test`
- `./scripts/run-live-smoke.sh`

## 7. 最新复验结果

- `2026-04-21` 已再次实际复验 `./scripts/mvnw-local.sh -q -DskipTests compile`，结果通过。
- `2026-04-21` 已在接入"历史关键记忆卡片"后再次实际复验 `./scripts/mvnw-local.sh -q -DskipITs test`，结果通过，汇总结果为 `tests=728, failures=0, errors=0, skipped=0`。
- `2026-04-21` 已再次实际复验 `./scripts/run-live-smoke.sh`，结果为 `tests=1, failures=0, errors=0, skipped=0`。
- 本轮实际 live smoke 生效配置为显式 `OPENMANUS_LIVE_*`：`models=glm-5.1`、`baseUrl=https://ruoli.dev/v1`，未依赖 `OPENMANUS_LLM_DEFAULT_LLM_*` 或 `OPENAI_*` 回退。

## 8. 当前收口判断

- 当前阶段代码实现、默认测试和分层守卫与 `AGENTS.md`、`DEVELOPMENT_GOALS.md` 保持一致。
- 默认 `compile` 与 `test` 已满足当前阶段仓库内主线基线，`live smoke` 也已拿到 non-skipped 成功证据；阶段 B 两个切片均已形成可回归验证的最小闭环。
- 下一步将进行代码结构精简，以 `agent/context` 包为主，精简后再评估是否进入阶段 C、MCP 资源融合或 Multi-Agent。
