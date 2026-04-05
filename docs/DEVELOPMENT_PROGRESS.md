# 开发进度

## 当前阶段状态

- 日期：**2026-04-06**
- 阶段：**阶段 A 验收收口**
- 状态：**Blocked**
- 阶段边界：只收口“单 Agent + 上下文治理 A + CodeAct A + 最小工具结果压缩/按需回填”。
- 阶段结论：当前代码与方案边界保持一致；`./scripts/mvnw-local.sh -q -DskipTests compile` 与 `./scripts/mvnw-local.sh -q -DskipITs test` 已复验通过；阶段 A 当前唯一阻塞仍是真实 Provider live smoke 验收未闭环。

## 当前阻塞

- 缺少 `OPENMANUS_LIVE_ANTHROPIC_MODEL`、`OPENMANUS_LIVE_ANTHROPIC_BASE_URL`、`OPENMANUS_LIVE_ANTHROPIC_API_KEY`。
- 缺少 `OPENMANUS_LIVE_GEMINI_MODEL`、`OPENMANUS_LIVE_GEMINI_BASE_URL`、`OPENMANUS_LIVE_GEMINI_API_KEY`。
- 对应 `OPENMANUS_LLM_PROVIDERS_ANTHROPIC_*` 与 `OPENMANUS_LLM_PROVIDERS_GEMINI_*` fallback 当前也无可用值。
- 在 OpenAI、Anthropic、Gemini 三条 live smoke 都产出 non-skipped 结果前，阶段 A 不能收口。

## 当前主线

- 只推进阶段 A 验收收口，不扩展上下文治理 B/C、MCP 资源融合或 `Multi-Agent` 默认实现面。
- 先补齐 Anthropic 与 Gemini live smoke 环境，再复跑统一验收脚本。
- 在 live smoke 进入仓库代码路径前，冻结阶段 A 代码面，不为缺环境问题新增兜底逻辑。
- 如果脚本进入仓库代码路径后失败，只处理首个非环境失败点，并补齐直接相关测试与文档。

## 下一步入口

1. 补齐 Anthropic 与 Gemini 的 `OPENMANUS_LIVE_*` 环境变量，或补齐对应 provider profile fallback。
2. 重新执行 `./scripts/run-live-smoke.sh`，确认 OpenAI、Anthropic、Gemini 三条链路是否全部产出 non-skipped 结果。
3. 如果脚本进入仓库代码路径后失败，只修复首个非环境失败点，并同步补齐该失败点的直接相关测试与文档。
