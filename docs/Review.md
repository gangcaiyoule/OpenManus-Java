# Review

## 当前结论

- 结论：**Changes Requested**
- 阶段状态：**Blocked**
- 结论日期：**2026-04-06**
- 结论摘要：按 `AGENTS.md -> docs/DEVELOPMENT_GOALS.md -> docs/TECHNICAL_IMPLEMENTATION.md` 复核当前实现、架构守卫与验证入口后，当前代码仍收口在“单 Agent + 上下文治理 A + CodeAct A + 最小工具结果压缩/按需回填”边界内，未见默认运行面提前落入 `Multi-Agent` 或 MCP 资源融合。当前唯一有效阻塞仍是真实 Provider live smoke 未闭环。

## 当前有效结论

- 目标一致性：复核 `AgentService`、`WorkflowStreamService`、`UnifiedWorkflowPortAdapter`、`UnifiedWorkflow`、`UnifiedAgentConfig`、`AbstractAgentExecutor`、`ContextAssembler`、`McpRuntimeConfig` 与 `SingleAgentArchitectureGuardTest` 后，主链路仍是单 Agent；`Multi-Agent` 仍停留在研究目标，没有进入默认实现面。
- 实现边界：`domain -> port -> infra adapter` 分层仍成立。`domain/service` 继续通过 `WorkflowExecutionPort` 与 `WorkflowExecutionEventPort` 收口工作流和监控；MCP 工具仍需 `openmanus.mcp.enabled=true` 才会接入 `UnifiedAgentConfig` 默认注册面。
- 代码质量：本轮未发现新的过度抽象、层级混乱或实验逻辑回流到 Controller / 配置层。上下文治理仍集中在 `agent/context`，工具结果压缩、offload、按需回填保持在单 Agent 执行闭环内部，没有向 `domain` 渗透。
- 测试覆盖：`2026-04-06` 已实际执行 `./scripts/mvnw-local.sh -q -DskipTests compile`，通过；已实际执行 `./scripts/mvnw-local.sh -q -DskipITs test`，通过。离线回归对分层边界、上下文治理、监控收口、沙箱适配、Web 代理和配置回退已有覆盖，但真实 Provider 验收仍不完整。

## 当前有效问题

### 1. 真实 Provider 验收仍未闭环

- 严重性：阻塞
- 位置：`scripts/run-live-smoke.sh`
- 事实：
  - `2026-04-06` 实际执行 `./scripts/run-live-smoke.sh`，脚本在 Maven 启动前失败。
  - 当前失败原因为 Anthropic / Gemini live smoke 环境变量缺失，不是仓库内 Java 代码、编译或单测失败。
  - 当前缺少以下真实且非空环境变量：
    - `OPENMANUS_LIVE_ANTHROPIC_MODEL`
    - `OPENMANUS_LIVE_ANTHROPIC_BASE_URL`
    - `OPENMANUS_LIVE_ANTHROPIC_API_KEY`
    - `OPENMANUS_LIVE_GEMINI_MODEL`
    - `OPENMANUS_LIVE_GEMINI_BASE_URL`
    - `OPENMANUS_LIVE_GEMINI_API_KEY`
  - 脚本已支持 `OPENMANUS_LLM_PROVIDERS_ANTHROPIC_*` 与 `OPENMANUS_LLM_PROVIDERS_GEMINI_*` fallback，但当前环境同样没有可用值。
- 与目标/方案的冲突：
  - `AGENTS.md` 要求按“小步推进、逐步完成”收口，当前不能绕过验收入口继续扩实现面。
  - `docs/TECHNICAL_IMPLEMENTATION.md` 已将 `./scripts/run-live-smoke.sh` 作为阶段 A 验收口径。
  - 在 OpenAI / Anthropic / Gemini 三条链路都形成 non-skipped 结果前，阶段 A 不能判定完成。
- 测试覆盖结论：
  - 当前测试覆盖对离线链路是充分的。
  - 当前测试覆盖对阶段 A 整体验收仍不完整，因为缺少 Anthropic / Gemini 的真实 Provider non-skipped 结果。

## 最小修正路径

1. 在当前 shell 或仓库根目录 `.env` 中补齐 Anthropic / Gemini 两组真实且非空的 `OPENMANUS_LIVE_*` 变量，或补齐对应 provider profile fallback。
2. 重新执行 `./scripts/run-live-smoke.sh`。
3. 若仍失败，只处理脚本输出的首个失败点，不扩展阶段 A 范围。
