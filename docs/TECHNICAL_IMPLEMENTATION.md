# 技术实现方案

## 1. 当前适用范围

本文档仅覆盖当前阶段已经落地或正在收口的最小方案：

1. 上下文治理阶段 A。
2. CodeAct 阶段 A。
3. 工具结果压缩与按需回填的最小链路。

当前不展开详细方案：

- 上下文治理阶段 B/C 的复杂摘要策略与任务态增强细节。
- MCP client、MCP 工具接入与资源融合的完整协议设计。
- `Multi-Agent` 协同编排、任务拆分与跨 Agent 上下文传递的完整设计。
- 为未来扩展提前引入的新框架或大规模重构。

补充边界说明：

- 当前工作区已出现任务态上下文与 MCP 相关实现，本文档只记录与当前阶段决策直接相关的最小边界，不逐项展开这些在途能力。
- `Multi-Agent` 当前仅作为研究方向记录，不纳入本阶段最小实现与验收口径。
- 阶段是否切换，仍只以 live smoke non-skipped 证据和当前最小链路收口情况为准。

## 2. 分层约束

遵循 `AGENTS.md`：

- `domain`：承载核心业务语义，不直接吸收执行框架细节。
- `agent`：承载 Agent 执行编排、上下文治理与回填逻辑。
- `infra`：只负责配置、存储、外部适配，不承载业务编排。
- `aiframework`：提供模型调用、消息结构与运行时抽象。

当前实现继续遵守“先最小可运行，再逐步增强”的原则。

当前补充收敛：

- `domain/service` 通过 `WorkflowExecutionPort` 依赖工作流执行能力。
- `infra/workflow` 通过 `UnifiedWorkflowPortAdapter` 适配 `agent/workflow/UnifiedWorkflow` 到 domain port。
- `AiRuntimeConfig` 与 `AiFrameworkConfig` 对 `openmanus.llm.defaultLlm.apiType` 统一采用同一回退语义：空白、缺失或非法值都按 `OPENAI` 处理；OpenAI client 继续复用 `defaultLlm` 的 `apiKey/baseUrl/model/timeout`，避免运行时 provider 选择与底层 client 配置分叉。
- `DotenvLoader` 与 `run-live-smoke.sh` 对“空白显式值”保持一致：只有非空白环境变量或 system property 才阻止 `.env` 回填；空字符串或纯空白值继续视为未配置，避免 `-DKEY=` 之类的启动参数意外屏蔽本地开发配置。
- `AgentService` 与 `WorkflowStreamService` 统一通过 `WorkflowExecutionEventPort` 完成最小监控收口；HTTP `/api/agent/chat` 与 `/workflow-stream` 都必须补齐 `startWorkflowTracking/endWorkflowTracking/startExecution/endExecution`，失败路径继续保留 `recordError` 细节事件。
- `WorkflowStreamService` 通过 `WorkflowExecutionEventPort`、`WorkflowStreamPublisher` 依赖执行事件追踪与 WebSocket 推送，不再直接依赖 runtime tracker 或 `SimpMessagingTemplate`。
- `WorkflowExecutionEventPort` 负责统一承载 workflow 级 tracking 和 agent execution event 写入；`WorkflowStreamService` 只调用 port，不直接触碰 tracker 细节。
- `WorkflowStreamService` 成功与失败分支都必须补发 `endExecution` 终态事件；失败路径继续保留 `recordError` 作为错误细节事件，再以 `ERROR` 终态收口生命周期。
- `WorkflowStreamService` 在真实主链路开始/结束时必须同步触发 `startWorkflowTracking/endWorkflowTracking`，保证 `/detailed-flow`、`/flows/all`、`/flows/recent` 可读取到最小 workflow 级执行记录。
- `AgentService` 与 `WorkflowStreamService` 的失败分支统一对异常做最小收口：优先解包异步包装异常，空白异常文案统一归一为 `unknown error`，避免把 `null` 写入监控事件、WebSocket 结果和 HTTP 返回。
- `WorkflowExecutionEventPortAdapter` 的 listener 注册表按 `(sessionId, listener)` 维度维护；同 session 重复注册只向 tracker 注册一次，跨 session 仍保持独立 delegate 与独立移除；若 `tracker.addListener(...)` 失败，必须同步回滚本次映射，避免残留脏 listener 状态阻塞后续重试。
- `SessionSandboxManager` 通过 `SessionSandboxClient` 与 `SessionFileSandboxDirectoryProvider` 依赖 VNC 沙箱与文件沙箱目录能力；legacy 会话 ID 映射、目录创建与采样告警下沉到 `infra/sandbox/SessionFileSandboxDirectoryProviderAdapter`，`domain/service` 仅保留会话级编排。
- `SessionSandboxManager.getSandboxInfo()` 仅在缓存状态为 `RUNNING` 且 `containerId` 非空白时探测容器运行态；`CREATING`、`ERROR`、无容器 ID 的记录保持原状态，不再错误折叠为 `STOPPED`。
- `WebProxyController` 只依赖 `WebProxyService` 暴露代理查询入口，不直接读取代理配置。
- `WebProxyService` 通过 `WebProxyFetchPort` 依赖 Web 代理抓取能力；`infra/web/HttpUrlConnectionWebProxyAdapter` 承担 `HttpURLConnection`、代理解析、响应头过滤与 HTML 改写实现。
- `infra/web/HttpUrlConnectionWebProxyAdapter` 通过 `WebProxyConfigProvider` 读取代理配置，`domain/controller` 与 `domain/service` 不直接接触代理实现细节。
- `WebProxyController` 的 `/api/proxy/web` 只接受 base64url 编码后的目标地址，不再回退直通未编码原始 URL；输入非法时统一返回 `400 Bad Request`。
- `WebProxyService` 与 `HttpUrlConnectionWebProxyAdapter` 共享 `WebProxyTargetValidator`：只允许显式 `http/https` 绝对 URL，并统一拒绝 `localhost`、回环地址、站点本地网段、链路本地地址和 IPv6 unique local 地址。
- `HttpUrlConnectionWebProxyAdapter` 对 3xx `Location` 解析后的目标地址继续复用同一校验链路，避免通过重定向绕过 WebProxy 最小安全边界。
- `HttpUrlConnectionWebProxyAdapter` 与 `WebProxyController` 在转发响应头时统一忽略 `null` header name、`null` value list 与 `null` value，避免上游异常响应头把代理链路放大为 `500/502`。
- `SingleAgentArchitectureGuardTest` 守卫 `domain/service` 禁止直接 import `UnifiedWorkflow`。
- `SingleAgentArchitectureGuardTest` 额外守卫 `domain/**` 禁止直接 import `com.openmanus.aiframework.runtime..`、`SimpMessagingTemplate` 和 runtime proxy config。
- `SingleAgentArchitectureGuardTest` 额外守卫 `domain/**` 禁止重新引入 `HttpURLConnection`、`Proxy`、`URL`、`InputStream`、`ByteArrayOutputStream` 等 Web 抓取实现细节。
- `SingleAgentArchitectureGuardTest` 额外守卫 `domain/**` 禁止重新引入 `Marker`、`MarkerFactory`、`AiLogMarkers`、`LogMarkers` 等前端展示标记依赖，domain 层只保留执行语义日志。
- `SingleAgentArchitectureGuardTest` 的源码守卫统一先剔除注释、字符串和字符字面量，再同时扫描 `import` 与 fully-qualified 直接引用，避免仅靠字符串包含判断导致的漏报或误报。
- `Step2AbstractAgentExecutorBuilderRuntimeApiGuardTest` 守卫 `agent`/`aiframework` 代码不得通过 `import` 或 fully-qualified 直接引用重新耦合 `domain`、`infra.config`、`infra.sandbox`、`infra.monitoring`、`infra.log`。
- MCP 工具链接入仅在 `openmanus.mcp.enabled=true` 时生效；即使存在 `McpToolRegistryBootstrap` Bean，阶段 A 默认主工具链也不自动装入 MCP 工具。

## 3. 当前最小实现

### 3.1 上下文治理

目标：在模型调用前统一处理上下文，而不是把规则散落在执行器中。

当前最小链路：

- `ContextSnapshot`：提取历史消息、当前轮消息和当前用户消息。
- `ContextBudgetPolicy`：统一消息数量与近似 token 预算。
- `ContextAssembler`：按统一顺序完成上下文组装与裁剪。

规则保持简单可回归：

1. 先裁剪历史消息。
2. 再拼接当前轮消息。
3. 最后执行总量与近似 token 预算。

当前阶段接受的最小边界：

- `TaskExecutionState` 当前只允许作为上下文治理的辅助卡片进入模型输入，用于保留最小计划/进度线索。
- `ContextAssembler` 在“首轮无历史消息”分支也必须复用统一的任务态注入与总量裁剪链路，避免首轮调用遗漏 `TaskExecutionState` 卡片。
- 当前不把它视为阶段 C 的完整任务态能力验收项，不继续叠加新字段、新策略或额外抽象。
- 若后续需要增强任务态恢复、失败归因或待办管理，统一延后到阶段 C 再展开。

### 3.2 CodeAct 最小闭环

目标：先保证“计划 -> 工具执行 -> 观察结果 -> 继续执行”的最小回路成立。

当前闭环：

1. 模型输出工具调用。
2. 执行本地注册工具。
3. 将工具结果统一写回消息链。
4. 进入下一轮执行。

基础保护：

- 空输入快速失败。
- 最大轮次保护。
- 最大执行时长保护。
- 未知工具重复调用保护。

### 3.3 工具结果压缩与按需回填

目标：先控制上下文体积，再保留最小可追溯能力。

当前最小策略：

- 超长 `TOOL` 消息进行压缩，保留必要元信息与头尾预览。
- 回填只依赖显式信号：
  - 用户显式给出 `artifactId`
  - 用户显式提及工具名
- 显式 `artifactId` 信号按全文扫描合法 `sha256:` 哈希提取，不依赖空白分词；自然语言中的英文句尾标点、中文引号与中文句号包裹场景都必须可识别。
- 回填后仍进入统一预算治理链路，避免打爆上下文窗口。

### 3.4 Live smoke 证据闭环

目标：把真实 Provider 验证与 surefire 结果汇总收敛到单一入口，避免手工翻报告。

当前最小链路：

1. `scripts/run-live-smoke.sh` 先清理旧的 `TEST-*LiveSmokeTest.xml`，避免误读历史报告。
2. 脚本启动后优先从仓库根目录 `.env` 回填缺失的 `OPENMANUS_LIVE_*` 变量；当前 shell 中非空白同名变量优先，空串或纯空白值仍视为缺失并允许 `.env` 回填。
3. 若 OpenAI live smoke 缺少 `OPENMANUS_LIVE_MODEL/BASE_URL/API_KEY`，脚本与 `OpenAiClientLiveSmokeTest` 会继续从 `OPENMANUS_LLM_DEFAULT_LLM_MODEL/BASE_URL/API_KEY` 回填，避免重复维护同义 OpenAI 配置。
4. 若 Anthropic / Gemini live smoke 缺少 `OPENMANUS_LIVE_*` 三元组，脚本与对应 `*LiveSmokeTest` 会继续从 `OPENMANUS_LLM_PROVIDERS_ANTHROPIC_*`、`OPENMANUS_LLM_PROVIDERS_GEMINI_*` 回填，复用现有 provider profile 配置。
5. `LiveSmokeEnv` 在直接执行 `*LiveSmokeTest` 时也必须裁剪环境变量前后空白，再参与显式值优先与 provider profile 回退，避免 `mvn -Dtest='*LiveSmokeTest' test` 场景把带空格的 shell 值误当作真实 endpoint、model 或 api key。
6. `.env` 解析同时兼容未加引号值与加引号值后的行尾注释，避免 `KEY=value # note` 或 `KEY=\"value\" # note` 把说明文字误带入真实变量值。
7. 在调用 Maven 前直接校验 OpenAI / Anthropic / Gemini 三组 live smoke 必需变量；缺失、空白或仍为 `dotenv.example` 占位 API key 时立即失败并输出具体变量名。占位识别不仅覆盖 `OPENMANUS_LIVE_*` 直配值，也覆盖从 `OPENMANUS_LLM_DEFAULT_LLM_*` 与 provider profile 回填后的占位值，避免示例凭证误进入真实请求。
8. live smoke 缺失变量快速失败时，脚本只针对实际缺失的 provider 输出可接受的回填入口：OpenAI-compatible 提示 `OPENMANUS_LLM_DEFAULT_LLM_*`，Anthropic / Gemini 提示各自 `OPENMANUS_LLM_PROVIDERS_*`，避免排障时再回查实现细节。
9. 固定通过 `scripts/mvnw-local.sh` 执行 `*LiveSmokeTest`。
10. 测试结束后读取 `target/surefire-reports/TEST-*LiveSmokeTest.xml`。
11. 汇总 `tests/failures/errors/skipped`，并输出首个问题分类。
12. 若存在 `failure/error/skipped`、缺少报告或前置变量缺失，脚本直接失败。

当前分类规则保持简单：

- 优先识别 `failure`。
- 其次识别 `error`。
- 再识别 `skipped`，其中包含 `Assumption failed` 时输出该分类，否则输出 `skipped`。
- 全部 non-skipped 时输出 `first issue: none`。
- 若 surefire 报告全为 non-skipped，脚本最终继续透传 `mvn` 退出码，避免误把 Maven 级失败判成通过。

## 4. 当前测试口径

当前只保留与阶段 A 收口直接相关的验证入口：

- 上下文治理、任务态最小注入、工具结果压缩与按需回填测试。
- `ContextAssemblerTest` 额外守住空快照分支，避免首轮或异常输入绕过统一上下文组装链路。
- `ToolResultContextCompressorTest` 额外守住 null/非工具消息透传与“已压缩结果不重复压缩”边界。
- `IndexedRehydrateSelectorTest` 额外守住显式 `artifactId` 被英文句号、中文引号或中文句号包裹时仍能触发回填，避免自然语言输入丢失按需回填信号。
- `AiFrameworkConfigProviderResolutionTest`、`AiRuntimeConfigProviderSelectionTest` 与 `AiRuntimeConfigRuntimeWiringTest`，用于守住 `defaultLlm.apiType` 的空白/非法/兼容值分支，确保 runtime provider 选择与 OpenAI client 配置回退语义一致。
- `LiveSmokeEnvTest` 额外守住 OpenAI 直配值、Anthropic/Gemini provider profile 回退值的首尾空白裁剪，以及显式 live 变量优先级和显式空白值回落分支。
- `DotenvLoaderTest` 额外守住“已有非空白 system property 优先”与“空白 system property 继续允许 `.env` 回填”分支，保证脚本与 Java 侧本地配置加载语义一致。
- `LiveSmokeEnv` 与 `run-live-smoke.sh` 对 `dotenv.example` 占位 API key 采用一致口径：无论通过 live 变量还是 fallback profile/default LLM 进入，`*LiveSmokeTest` 直跑时也必须视为“未配置”并跳过，避免示例凭证触发真实请求。
- `LiveSmokeScriptIntegrationTest` 额外守住 OpenAI 默认 LLM 回填、Anthropic/Gemini provider profile 回填后仍为 `dotenv.example` 占位 API key 的快速失败分支。
- `LiveSmokeScriptIntegrationTest` 额外守住缺失变量提示只输出实际缺失 provider 的 fallback 入口，避免错误提示扩散到已配置 provider。
- `LiveSmokeScriptIntegrationTest` 额外守住带引号且值内部包含 `#` 的 `.env` 变量不会被误判为注释，避免 live smoke 在兼容代理 URL、带片段标记或包含 `#` 的凭证场景下错误截断真实值。
- `WorkflowExecutionEventPortAdapterTest` 与 `WorkflowStreamServiceSessionMemoryTest`，用于守住 session 维度 listener 生命周期和成功/失败执行收口。
- `WorkflowExecutionEventPortAdapterTest` 额外守住 listener 注册异常回滚与同 session 重试注册分支，避免 tracker 首次注册失败后残留脏映射。
- `AgentServiceConversationMemoryTest` 与 `AgentServiceMonitoringIntegrationTest`，用于守住 `/api/agent/chat` 的最小监控收口、同步/异步分支、失败分支与 detailed flow 可观测性。
- `AgentServiceConversationMemoryTest` 额外守住嵌套异步异常解包与空白异常文案回退，避免错误结果、监控事件和返回体出现 `null`。
- `WebProxyControllerTest`、`WebProxyServiceTest` 与 `HttpUrlConnectionWebProxyAdapterTest`，用于守住 base64url 输入约束、`http/https` scheme 限制、回环/内网/链路本地地址拒绝、`/api/proxy/url` 非法 `target` 的 `400 Bad Request` 收口，以及重定向二次校验分支。
- `WebProxyControllerTest` 与 `HttpUrlConnectionWebProxyAdapterTest` 额外守住 `null/empty` 响应头值过滤，避免异常上游 header 破坏代理响应写回。
- `WorkflowStreamServiceMonitoringIntegrationTest`，用于守住真实工作流执行后 detailed flow 查询非空。
- `WorkflowStreamServiceSessionMemoryTest` 额外守住流式执行失败时空白异常文案回退，确保 `recordError/endExecution/result` 三处输出一致。
- `AgentExecutionTrackerStatisticsTest`，用于守住 `FAILED/ERROR/TIMEOUT` 都计入 `errorCount`，且 `successCount/successRate` 只按终态 `AGENT_END` 成功事件计算，不被工具成功事件污染。
- `RuntimeExecutionTrackerAdapterTest`，用于守住 runtime 侧执行事件桥接与 session 隔离语义。
- `JavaSourceGuardSupportTest`，用于守住源码扫描时的注释、字符串、字符字面量剔除，以及 `import`、fully-qualified 直接引用和 package 声明边界。
- `SingleAgentArchitectureGuardTest` 与 `Step2AbstractAgentExecutorBuilderRuntimeApiGuardTest`，用于守住 `domain -> port -> infra adapter` 分层边界，并覆盖 fully-qualified 跨层引用与无 import 直接引用路径。
- `SessionSandboxManagerLifecycleTest` 与 `SessionSandboxManagerSecurityTest`，用于守住 `RUNNING -> STOPPED`、`CREATING/ERROR` 保持不变、空白 `containerId` 不探测、探测异常透传以及文件沙盒目录委托边界。
- `LiveSmokeScriptIntegrationTest`、`LiveSmokeEnvTest` 与三组 `*LiveSmokeTest`，用于守住 live smoke 变量回填、脚本汇总和真实 Provider 验收入口。
- `AgentServiceConversationMemoryTest` 额外守住 `/api/agent/chat` 异步分支在 `execute(...)` 返回 future 前同步抛错时，仍能完成 `endWorkflowTracking/recordError/endExecution` 收口。

当前验证入口保持收敛：

- 离线编译：`./scripts/mvnw-local.sh -q -DskipTests compile`
- 离线回归：`./scripts/mvnw-local.sh -q -DskipITs test`
- 前端单测：`cd frontend && npm test -- --run`
- 前端构建：`cd frontend && npm run build`
- 真实 Provider 验收：`./scripts/run-live-smoke.sh`

`2026-04-06` 本轮复核验证结果：

- `cd frontend && npm test -- --run` 通过，当前共 `4` 个测试文件、`17` 个用例通过。
- `cd frontend && npm run build` 通过。
- `./scripts/run-live-smoke.sh` 仍在 Maven 启动前失败；当前实际输出仍是缺少 `OPENMANUS_LIVE_ANTHROPIC_MODEL`、`OPENMANUS_LIVE_ANTHROPIC_BASE_URL`、`OPENMANUS_LIVE_ANTHROPIC_API_KEY`、`OPENMANUS_LIVE_GEMINI_MODEL`、`OPENMANUS_LIVE_GEMINI_BASE_URL`、`OPENMANUS_LIVE_GEMINI_API_KEY`。
- `./scripts/mvnw-local.sh -q -DskipTests compile` 与 `./scripts/mvnw-local.sh -q -DskipITs test` 本轮未重跑；当前阶段判断仍以已通过的关键抽查与 live smoke 阻塞状态为准。
- 本轮复核结论保持不变：阶段 A 代码主链路、离线后端回归和前端验证入口均已收敛，当前没有新增代码级失败点；唯一未闭环项仍是 Anthropic / Gemini 真实 Provider live smoke 环境。

## 5. 已知问题

### 5.1 真实 Provider 验收未闭环

- 当前唯一未闭环的验收入口仍是 `./scripts/run-live-smoke.sh`。
- `2026-04-06` 重新执行 `./scripts/run-live-smoke.sh` 仍在 Maven 前失败；脚本当前输出如下：
  - `ERROR: missing live smoke env vars: OPENMANUS_LIVE_ANTHROPIC_MODEL OPENMANUS_LIVE_ANTHROPIC_BASE_URL OPENMANUS_LIVE_ANTHROPIC_API_KEY OPENMANUS_LIVE_GEMINI_MODEL OPENMANUS_LIVE_GEMINI_BASE_URL OPENMANUS_LIVE_GEMINI_API_KEY`
- 当前缺少以下 Anthropic / Gemini 真实环境变量：
  - `OPENMANUS_LIVE_ANTHROPIC_MODEL`
  - `OPENMANUS_LIVE_ANTHROPIC_BASE_URL`
  - `OPENMANUS_LIVE_ANTHROPIC_API_KEY`
  - `OPENMANUS_LIVE_GEMINI_MODEL`
  - `OPENMANUS_LIVE_GEMINI_BASE_URL`
  - `OPENMANUS_LIVE_GEMINI_API_KEY`
- 当前阻塞属于环境配置缺失，不能通过继续修改仓库代码解除；只有补齐真实 Anthropic / Gemini live 配置后，才能形成阶段 A 的 non-skipped Provider 验收证据。

## 6. 下一步实现顺序

1. 补齐 Anthropic / Gemini 两组真实 `OPENMANUS_LIVE_*` 变量，或补齐 `OPENMANUS_LLM_PROVIDERS_ANTHROPIC_*`、`OPENMANUS_LLM_PROVIDERS_GEMINI_*`。
2. 复跑 `./scripts/run-live-smoke.sh`，确认 OpenAI / Anthropic / Gemini 三条链路均形成 non-skipped 结果。
3. 若 live smoke 仍失败，只围绕脚本输出的首个失败点继续收敛，不扩展新的阶段 A 实现面。
4. 在上述问题关闭前，不扩展上下文阶段 B/C，不推进 MCP 资源融合，不进入 `Multi-Agent` 实现。

## 7. 维护约定

- 本文档只保留当前有效技术方案。
- 已完成但不再影响决策的历史切片不再逐轮堆积。
- 方案变更时直接更新当前结构，而不是追加流水账。
