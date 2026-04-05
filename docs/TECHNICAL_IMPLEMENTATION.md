# 技术实现方案

## 1. 当前阶段范围

当前阶段只保留以下实现面：

1. 上下文治理阶段 A。
2. CodeAct 阶段 A。
3. 最小工具结果压缩与按需回填。

当前不进入：

- 上下文治理阶段 B/C。
- MCP 资源融合。
- `Multi-Agent` 默认实现面。

阶段完成标准只看两项：

1. 单 Agent 最小链路稳定。
2. `./scripts/run-live-smoke.sh` 为 OpenAI、Anthropic、Gemini 三条链路产出 non-skipped 结果。

## 2. 当前有效分层

- `domain` 只保留业务语义和 port，不直接依赖运行时、监控推送、Web 抓取或框架细节。
- `agent` 负责单 Agent 执行编排、上下文治理、工具结果压缩/回填和最小任务态注入。
- `infra` 负责配置、存储、监控、沙箱、Web 代理和工作流 adapter。
- `aiframework` 负责模型、消息和工具运行时抽象。

当前必须守住的落地边界：

- `domain/service` 只通过 `WorkflowExecutionPort` 调工作流；`infra/workflow/UnifiedWorkflowPortAdapter` 负责对接 `UnifiedWorkflow`。
- `AgentService` 与 `WorkflowStreamService` 统一通过 `WorkflowExecutionEventPort` 收口 workflow tracking、execution start/end 和失败记录。
- `WorkflowStreamService` 只依赖 `WorkflowExecutionEventPort` 与 `WorkflowStreamPublisher`。
- `SessionSandboxManager` 只做会话级编排，运行时沙箱细节下沉到 `infra/sandbox`。
- `WebProxyController` 与 `WebProxyService` 只依赖 domain port；URL 校验、请求转发、重定向复检和响应头过滤下沉到 `infra/web`。
- MCP 工具接入继续受 `openmanus.mcp.enabled=true` 保护，阶段 A 默认主链路不自动装入 MCP 工具。

## 3. 当前最小可运行链路

### 3.1 上下文治理

- `ContextSnapshot` 统一提取历史消息、当前轮消息和当前用户输入。
- `ContextBudgetPolicy` 统一消息数和 token 预算。
- `ContextAssembler` 按固定顺序组装并裁剪上下文。
- `ToolResultContextCompressor` 对超长工具结果做最小压缩。
- `IndexedRehydrateSelector` 保留按需回填入口。
- `TaskExecutionState` 只保留最小任务态卡片。

当前规则保持简单：

1. 先裁剪历史消息。
2. 再合并当前轮消息。
3. 最后执行总量与 token 预算控制。

### 3.2 CodeAct 最小闭环

- `AbstractAgentExecutor` 负责单轮执行循环和工具调用编排。
- 本地工具继续通过统一工具注册机制接入。
- 工具结果统一写回对话上下文，供下一轮观察与调整。
- MCP 相关代码只保留最小接入点，不进入默认执行面。

### 3.3 监控、沙箱与代理收口

- `/api/agent/chat` 与 `/workflow-stream` 都必须产出最小 workflow tracking 和 execution 终态。
- 异常统一解包到根因，空白异常文案统一归一为 `unknown error`。
- `SessionSandboxManager.getSandboxInfo()` 只在缓存为 `RUNNING` 且 `containerId` 非空时探测运行态。
- `/api/proxy/web` 只接受 base64url 目标地址；代理仅允许显式 `http/https` 绝对 URL，并拒绝回环、本地和链路本地地址。

## 4. 验证口径

当前有效验证入口：

- `./scripts/mvnw-local.sh -q -DskipTests compile`
- `./scripts/mvnw-local.sh -q -DskipITs test`
- `./scripts/run-live-smoke.sh`

当前必须守住的覆盖面：

- 上下文组装、预算、压缩与回填选择。
- `AgentService` / `WorkflowStreamService` 主流程、分支、异常和边界。
- 工作流监控事件收口与 listener 生命周期。
- Web 代理输入校验、重定向校验和异常响应头过滤。
- 沙箱生命周期与状态探测边界。
- 配置回退、`.env` 回填和 live smoke 脚本一致性。
- 架构守卫，确保 `domain -> port -> infra adapter` 分层不回退。

`2026-04-06` 实际复验结果：

- `./scripts/mvnw-local.sh -q -DskipTests compile` 通过。
- `./scripts/mvnw-local.sh -q -DskipITs test` 通过。
- `./scripts/run-live-smoke.sh` 失败，首个失败点仍在 Maven 启动前的环境校验，不是仓库内代码路径失败。

## 5. 当前阻塞与处理原则

当前唯一阻塞仍是 Anthropic / Gemini 真实 Provider live smoke 环境缺失。当前缺少：

- `OPENMANUS_LIVE_ANTHROPIC_MODEL`
- `OPENMANUS_LIVE_ANTHROPIC_BASE_URL`
- `OPENMANUS_LIVE_ANTHROPIC_API_KEY`
- `OPENMANUS_LIVE_GEMINI_MODEL`
- `OPENMANUS_LIVE_GEMINI_BASE_URL`
- `OPENMANUS_LIVE_GEMINI_API_KEY`

对应 provider profile fallback 也未提供可用值：

- `OPENMANUS_LLM_PROVIDERS_ANTHROPIC_MODEL`
- `OPENMANUS_LLM_PROVIDERS_ANTHROPIC_BASE_URL`
- `OPENMANUS_LLM_PROVIDERS_ANTHROPIC_API_KEY`
- `OPENMANUS_LLM_PROVIDERS_GEMINI_MODEL`
- `OPENMANUS_LLM_PROVIDERS_GEMINI_BASE_URL`
- `OPENMANUS_LLM_PROVIDERS_GEMINI_API_KEY`

当前处理原则：

1. 不再扩展阶段 A 代码面。
2. 先补齐真实 Provider 环境。
3. 复跑 `./scripts/run-live-smoke.sh` 后，只处理脚本输出的首个失败点。
