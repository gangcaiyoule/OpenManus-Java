# 开发进度

## 当前阶段状态

- 日期：**2026-04-06**
- 阶段：**阶段 A 验收收口**
- 状态：**Blocked**
- 阶段边界：只收口“单 Agent + 上下文治理 A + CodeAct A + 最小工具结果压缩/按需回填”；不进入上下文治理 B/C、MCP 资源融合或 `Multi-Agent` 实现。
- 当前结论：本轮 reviewer 复核未发现新的代码级偏差，阶段 A 仍只差 Anthropic / Gemini 真实 Provider live smoke non-skipped 证据；当前不扩代码范围。

## 当前主线

- 主线目标：形成阶段 A 的真实 Provider non-skipped 验收证据，作为当前阶段是否关闭的唯一入口。
- 开发顺序：
  1. 补齐 Anthropic / Gemini live smoke 所需真实配置。
  2. 复跑 `./scripts/run-live-smoke.sh`。
  3. 若仍失败，只处理脚本输出的首个失败点。
- 范围约束：
  - 不扩大上下文治理到阶段 B/C。
  - 不推进 MCP 资源融合。
  - 不进入 `Multi-Agent` 实现。
  - 不以新增代码绕过验收环境缺口。

## 当前阻塞

- `./scripts/run-live-smoke.sh` 仍未形成 Anthropic / Gemini 的 non-skipped 验收结果。
- `2026-04-06` 当前首个阻塞仍是缺少以下真实且非空变量：
  - `OPENMANUS_LIVE_ANTHROPIC_MODEL`
  - `OPENMANUS_LIVE_ANTHROPIC_BASE_URL`
  - `OPENMANUS_LIVE_ANTHROPIC_API_KEY`
  - `OPENMANUS_LIVE_GEMINI_MODEL`
  - `OPENMANUS_LIVE_GEMINI_BASE_URL`
  - `OPENMANUS_LIVE_GEMINI_API_KEY`
- 脚本允许使用 `OPENMANUS_LLM_PROVIDERS_ANTHROPIC_*` 与 `OPENMANUS_LLM_PROVIDERS_GEMINI_*` 作为 fallback，但当前环境同样未提供可用值。
- 该阻塞属于验收环境缺口，不应通过继续扩代码范围规避。

## 当前验证结果

- `2026-04-06` 已复核：
  - `./scripts/mvnw-local.sh -q -DskipITs -Dtest='SingleAgentArchitectureGuardTest,Step2AbstractAgentExecutorBuilderRuntimeApiGuardTest,ContextAssemblerTest,AgentServiceConversationMemoryTest,WorkflowStreamServiceSessionMemoryTest,WorkflowExecutionEventPortAdapterTest,SessionSandboxManagerLifecycleTest,McpToolRegistryBootstrapTest,WebProxyControllerTest,UnifiedAgentConfigRuntimeEntryDelegationTest' test`，通过。
  - `cd frontend && npm test -- --run`，通过；当前共 `4` 个测试文件、`17` 个用例通过。
  - `cd frontend && npm run build`，通过。
  - `./scripts/run-live-smoke.sh`，仍在 Maven 启动前因 Anthropic / Gemini live 变量缺失而失败。
- `2026-04-06` 本轮未重跑：
  - `./scripts/mvnw-local.sh -q -DskipTests compile`
  - `./scripts/mvnw-local.sh -q -DskipITs test`

## 下一步入口

1. 在当前 shell 或仓库根目录 `.env` 中补齐 Anthropic / Gemini 两组真实且非空的 `OPENMANUS_LIVE_*` 变量，或补齐对应 provider profile fallback。
2. 重新执行 `./scripts/run-live-smoke.sh`，确认 OpenAI / Anthropic / Gemini 三条链路均形成 non-skipped 结果。
3. 若 live smoke 仍失败，只处理首个失败点，并同步更新 `docs/DEVELOPMENT_PROGRESS.md`。
4. 只有在 live smoke 阻塞关闭后，才重新判断是否做阶段 A 收口 commit。
