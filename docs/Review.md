# Review

## 当前结论

- 结论：**Approved (with live smoke caveat)**
- 阶段状态：**仓内验收通过，外部 live smoke 受 TLS 环境阻塞**
- 结论日期：**2026-05-07**
- 结论摘要：当前代码收口在"单 Agent + 上下文治理 A/B + CodeAct A + 工具结果预算/卸载/显式读取"边界内，未见默认运行面提前落入 `Multi-Agent` 或 MCP 资源融合。仓内 compile + test 全部通过。外部 live smoke 受 TLS 证书链环境阻塞，不影响仓内持续推进。

## 当前有效结论

- 目标一致性：复核 `AgentService`、`WorkflowStreamService`、`AbstractAgentExecutor`、`ContextAssembler`、`McpRuntimeConfig` 后，主链路仍是单 Agent；`Multi-Agent` 仍停留在研究目标，没有进入默认实现面。
- 实现边界：`domain -> port -> infra adapter` 分层仍成立。`domain/service` 继续通过 port 收口工作流和监控；MCP 工具仍需 `openmanus.mcp.enabled=true` 才会接入默认注册面。
- 代码质量：未发现过度抽象、层级混乱或实验逻辑回流到 Controller / 配置层。上下文治理仍集中在 `agent/context`，工具结果预算/卸载/显式读取保持在单 Agent 执行闭环内部，没有向 `domain` 渗透。
- 测试覆盖：`2026-05-07` 已实际执行 `./scripts/mvnw-local.sh -q -DskipTests compile`，通过；已实际执行 `./scripts/mvnw-local.sh -q -DskipITs test`，通过。离线回归对分层边界、上下文治理、监控收口、沙箱适配、Web 代理和配置回退已有覆盖，但真实 Provider 验收仍不完整。

## 当前有效问题

### 1. 真实 Provider 验收仍未闭环

- 严重性：阻塞（不影响仓内持续推进）
- 位置：`scripts/run-live-smoke.sh`
- 事实：
  - 外部 TLS 证书链不在当前 JVM 默认信任集中，导致 `PKIX path building failed`。
  - 可通过 `OPENMANUS_LIVE_CA_CERT_FILE` 提供 PEM 证书链解决。
  - 此阻塞为外部环境问题，不是仓库内 Java 代码、编译或单测问题。
- 与目标/方案的冲突：
  - 在 OpenAI / Anthropic / Gemini 三条链路都形成 non-skipped 结果前，阶段整体验收不完整。
  - 但此阻塞不阻碍仓内代码的持续推进。

## 最小修正路径

1. 在当前 shell 或仓库根目录 `.env` 中补齐 `OPENMANUS_LIVE_CA_CERT_FILE` 或对应 provider 的 `OPENMANUS_LIVE_*` 变量。
2. 重新执行 `./scripts/run-live-smoke.sh`。
3. 若仍失败，只处理脚本输出的首个失败点，不扩展阶段范围。