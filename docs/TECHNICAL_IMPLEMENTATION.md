# 技术实现方案

## 1. 当前阶段范围

当前阶段只保留以下实现面：

1. 上下文治理阶段 A。
2. CodeAct 阶段 A。
3. 上下文治理阶段 B 第一切片：最小工具结果摘要化、卸载索引与按需回填。

当前不进入：

- 上下文治理阶段 B 后续切片 / C。
- MCP 资源融合。
- `Multi-Agent` 默认实现面。

当前阶段完成标准：

1. 单 Agent 最小链路稳定。
2. `./scripts/run-live-smoke.sh` 在当前默认 provider 路径上产出 non-skipped 结果。

当前阶段 provider 口径：

- 默认验收只看 OpenAI-compatible 主链路。
- Anthropic / Gemini 只保留配置兼容、接入点和测试预留，不作为阶段 A 收口前置条件。
- 在未重新提升阶段目标前，不为补齐 Anthropic / Gemini live smoke 扩张当前阶段范围。

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
- MCP 工具接入继续受 `openmanus.mcp.enabled=true` 保护，阶段 A 默认主链路不自动装入 MCP 工具。

## 3. 当前最小可运行链路

### 3.1 上下文治理

- `ContextSnapshot` 统一提取历史消息、当前轮消息和当前用户输入。
- `ContextSnapshot` 先按对象身份定位当前用户消息；命不中时再按等值消息从后向前回退，避免消息重建后把当前轮误拆到历史区。
- `ContextBudgetPolicy` 统一消息数和 token 预算。
- `ContextAssembler` 按固定顺序组装并裁剪上下文。
- `ToolResultContextCompressor` 对超长工具结果做最小压缩。
- `ToolResultContextCompressor` 输出固定摘要卡片，只保留 `keyFacts`、`recentActions`、`todo` 与必要预览。
- `ToolResultContextCompressor` 会继续收缩摘要字段与预览片段，确保压缩卡片自身仍受配置字符预算约束。
- `IndexedRehydrateSelector` 负责按需回填选择，不把回填决策扩散到 `domain`、Controller 或配置层。
- `TaskExecutionState` 只保留最小任务态卡片。

当前顺序固定为：

1. 先裁剪历史消息。
2. 再合并当前轮消息。
3. 最后执行总量与 token 预算控制。

### 3.2 CodeAct 最小闭环

- `AbstractAgentExecutor` 负责单轮执行循环和工具调用编排。
- 本地工具继续通过统一工具注册机制接入。
- 工具结果统一写回对话上下文；超长结果优先走“摘要/卸载卡片 + artifact”路径，供下一轮观察与调整。
- MCP 代码只保留最小接入点，不进入默认执行面。

### 3.3 工具结果摘要化、卸载与回填

- 超长工具结果可落到 `AiToolResultArtifactStore`，chat memory 中只保留 `[Tool Result Offloaded]` 索引卡片。
- `ContextAssembler` 在模型输入侧会把超长原始工具结果压缩成 `[Tool Result Context Compressed]` 卡片。
- 压缩卡片只保留三类摘要信号：`keyFacts`、`recentActions`、`todo`，以及必要的 `artifactId` / 预览信息。
- `IndexedRehydrateSelector` 只在以下条件之一成立时选择 artifact：
  1. 当前用户消息显式包含合法 `artifactId`。
  2. 当前用户消息明确提及有效工具名。
  3. 当前用户消息命中压缩卡片摘要中的关键事实、最近动作或待办。
- 仅因模型上下文里存在压缩卡片本身，不构成隐式回填信号，避免无关问题把旧 artifact 重新灌回模型。
- 回填结果统一以 `TOOL` 观察消息注入，并继续受单条字符上限、单轮数量上限和总量预算约束。

### 3.4 监控、沙箱与代理收口

- `/api/agent/chat` 与 `/workflow-stream` 都必须产出最小 workflow tracking 和 execution 终态。
- 异常统一解包到根因，空白异常文案统一归一为 `unknown error`。
- `SessionSandboxManager.getSandboxInfo()` 只在缓存为 `RUNNING` 且 `containerId` 非空时探测运行态。
- 运行态探测失败时保留最后一次缓存状态，避免会话查询路径因瞬时探测异常直接失败。
- `/api/proxy/web` 只接受 base64url 目标地址；代理仅允许显式 `http/https` 绝对 URL，并拒绝回环、本地和链路本地地址。

## 4. 验证口径

当前有效验证入口：

- `./scripts/mvnw-local.sh -q -DskipTests compile`
- `./scripts/mvnw-local.sh -q -DskipITs test`
- `./scripts/run-live-smoke.sh`

当前必须守住的覆盖面：

- 上下文组装、预算、压缩与回填选择。
- 压缩卡片摘要信号驱动的 indexed rehydrate 主流程、分支和边界。
- `AgentService` / `WorkflowStreamService` 主流程、分支、异常和边界。
- 工作流监控事件收口与 listener 生命周期。
- Web 代理输入校验、重定向校验和异常响应头过滤。
- 沙箱生命周期与状态探测边界。
- 配置回退、`.env` 回填和 live smoke 脚本一致性。
- 架构守卫，确保 `domain -> port -> infra adapter` 分层不回退。

当前已落地并通过的直接验证包括：

- `LiveSmokeEnvTest`：覆盖 OpenAI、Anthropic、Gemini 的显式 live env、provider/default fallback、空白值和 placeholder key 分支。
- `LiveSmokeScriptIntegrationTest`：覆盖 `run-live-smoke.sh` 的 `.env` 解析、OpenAI 必填、Anthropic/Gemini 可选启用、default/legacy OpenAI fallback、provider profile 回填、缺参/半配/placeholder fail-fast 和脚本结果汇总口径。
- `ValidationScriptsConsistencyTest`：约束 `dotenv.example` 与 live smoke 脚本所需变量保持一致。

`2026-04-06` 当前复验结果：

- `./scripts/mvnw-local.sh -q -DskipTests compile` 在当前工作树通过。
- `./scripts/mvnw-local.sh -q -DskipITs test` 在当前工作树通过。
- `./scripts/mvnw-local.sh -q -DskipITs -Dtest=ToolResultContextCompressorTest,ContextAssemblerTest,IndexedRehydrateSelectorTest,AbstractAgentExecutorChatMemoryIntegrationTest test` 在当前工作树通过。
- `./scripts/mvnw-local.sh -q -DskipITs -Dtest=HttpTransportTest,SseTransportTest,OpenAiClientIntegrationTest,OpenAiResponseParserTest test` 在当前工作树通过。
- `./scripts/mvnw-local.sh -q -DskipITs -Dtest=HttpTransportTest,SseTransportTest,OpenAiClientIntegrationTest,OpenAiResponseParserTest,ToolResultContextCompressorTest,ContextAssemblerTest,IndexedRehydrateSelectorTest,AbstractAgentExecutorChatMemoryIntegrationTest test` 在当前工作树通过。
- `./scripts/run-live-smoke.sh` 在 `2026-04-06` 当前环境复跑产出 `tests=1, failures=0, errors=1, skipped=0`；当前首个错误为 `OpenAiClientLiveSmokeTest` 的同步 `chat()` 收到 `503`，provider 返回 `{"error":{"message":"没有可用的账号","type":"server_error","param":"","code":null}}`。
- `LiveSmokeEnv` 与 `run-live-smoke.sh` 现在会把 `OPENAI_MODEL`、`OPENAI_BASE_URL`、`OPENAI_API_KEY` 作为 OpenAI-compatible live smoke 的最后一级兼容回退，与运行时 `OpenManusProperties` 的现有环境映射保持一致，避免“应用可跑但 live smoke 因口径不一致被跳过”。
- `OpenAiResponseParser` 与 `AbstractAiProviderClient` 当前已补齐两条防呆：JSON `error` payload 直接抛框架异常；同步 `chat()` 收到只有 usage、没有内容/工具/finish reason 的 SSE 成功体时显式判失败，避免把 provider 异常误落成空白答案。
- `HttpTransport` 现在会把 `2xx` 且以 `data:` / `event:` 开头的 SSE 正文包装为 transport 侧事件集合；`AbstractAiProviderClient.chat()` 复用现有 `parseStreamChunk()` 聚合 delta、finish reason、tool call 与 usage，兼容 OpenAI-compatible 同步路径返回 SSE 正文的情况。
- `HttpTransport` 与 `SseTransport` 当前已补齐一条最小 retry 兼容：当 provider 网关把上游临时故障包装成 `403 bad_response_status_code` 时，仍按可重试失败处理，不把瞬时上游状态直接固化为最终错误。
- `HttpTransportTest` 与 `SseTransportTest` 已补齐“vendor-wrapped upstream failure -> retry -> succeed”分支。
- OpenAI-compatible JSON `error` payload 与“只有 usage、没有内容/工具/finish reason 的空 SSE 成功体”现在会在 `aiframework client/parser` 边界被显式判失败。`2026-04-06` 当日实际抓到的同步 `chat()` `2xx` SSE 正文为“空 `choices` + `usage` + `[DONE]`”，没有任何文本 delta、tool call 或 finish reason，因此当前阻塞已收敛为外部 provider 未返回有效 completion，而不是仓库内遗漏了新的成功事件解析分支。
- `HttpTransportTest` 已补齐“`2xx` 成功状态返回 SSE 正文”分支；`OpenAiClientIntegrationTest` 已补齐“同步 `chat()` 收到 SSE 正文时仍能聚合出完整响应”主流程。
- `ContextSnapshotTest` 已覆盖“等值但非同一实例”的当前用户消息拆分，以及重复用户输入时优先命中最新一条。
- `ContextBudgetPolicyTest` 与 `ContextAssemblerTest` 已补齐“detached current user + total limit”组合场景，固定总量裁剪时优先保留持久化当前用户锚点与最新工具结果。
- `ToolResultContextCompressorTest` 已补齐“压缩卡片自身不得超过 `maxChars`”边界，验证摘要字段与预览片段会继续收缩直到预算内。
- `IndexedRehydrateSelectorTest` 与 `AbstractAgentExecutorChatMemoryIntegrationTest` 已补齐“命中压缩卡片摘要触发回填”和“压缩卡片存在但无摘要命中时不回填”场景。
- `WebProxyServiceTest`、`HttpUrlConnectionWebProxyAdapterTest` 与 `SingleAgentArchitectureGuardTest` 已复验 `WebProxy` 校验下沉到 `infra/web`，domain 侧只保留 port 编排与守卫约束。
- `LiveSmokeScriptIntegrationTest` 与 `ValidationScriptsConsistencyTest` 已复验通过，`run-live-smoke.sh` 现已收敛为 OpenAI-compatible 必需、Anthropic / Gemini 按配置可选启用。
- Anthropic / Gemini 仍只保留配置兼容、接入点和测试预留，不作为当前阶段默认验收前置条件。

## 5. 当前阻塞与处理原则

当前切片的唯一有效阻塞，是默认 OpenAI-compatible live smoke 仍未产出 non-skipped 成功结果。`2026-04-06` 当前环境最新复验里，`OpenAiClientLiveSmokeTest` 在同步 `chat()` 阶段直接收到 `503`，provider 返回 `没有可用的账号`，因此当前问题继续收敛为外部默认 provider 不可用。

处理原则：

1. 当前只继续维持“阶段 B 第一切片”的已落地范围，不扩展到阶段 B 后续切片 / C、MCP 资源融合或 `Multi-Agent` 默认实现面。
2. 维持 `WebProxy`、live smoke 脚本和当前分层边界，不把校验、传输或 provider 兼容逻辑回流到 `domain`。
3. 默认验收继续以 OpenAI-compatible 主链路为准；在外部 provider 恢复前，不扩写新的 provider 兼容分支，也不把 `503 server_error / 没有可用的账号` 误判为仓库内主链路回退。
4. 在 `compile`、`test` 与默认 live smoke 三项同时满足当前阶段口径前，不进行阶段完成判断，也不做新的阶段性 commit。
