# 开发进度

## 当前阶段状态

- 日期：**2026-04-06**
- 阶段：**阶段 A 验收收口**
- 状态：**Blocked**
- 阶段边界：只收口“单 Agent + 上下文治理 A + CodeAct A + 最小工具结果压缩/按需回填”。
- 当前结论：当前实现、分层和离线验证仍满足阶段 A 要求；本轮 review 未发现新的代码修复入口，阻塞继续只剩真实 Provider live smoke 验收。
- 当前验证：`./scripts/mvnw-local.sh -q -DskipTests compile` 通过，`./scripts/mvnw-local.sh -q -DskipITs test` 通过，`./scripts/run-live-smoke.sh` 仍因缺少 `OPENMANUS_LIVE_ANTHROPIC_*` 与 `OPENMANUS_LIVE_GEMINI_*` 在入口阶段失败。

## 当前阻塞

- 缺少 `OPENMANUS_LIVE_ANTHROPIC_MODEL`、`OPENMANUS_LIVE_ANTHROPIC_BASE_URL`、`OPENMANUS_LIVE_ANTHROPIC_API_KEY`。
- 缺少 `OPENMANUS_LIVE_GEMINI_MODEL`、`OPENMANUS_LIVE_GEMINI_BASE_URL`、`OPENMANUS_LIVE_GEMINI_API_KEY`。
- 对应 `OPENMANUS_LLM_PROVIDERS_ANTHROPIC_*` 与 `OPENMANUS_LLM_PROVIDERS_GEMINI_*` fallback 当前也无可用值。
- 在 OpenAI、Anthropic、Gemini 三条链路都产出 non-skipped 结果前，阶段 A 不能收口。

## 当前主线

- 主线目标：只完成阶段 A 验收收口，不再扩展上下文治理、CodeAct、MCP 或 Multi-Agent 实现面。
- 推进顺序：先补齐 Anthropic / Gemini live smoke 环境，再复跑统一验收脚本，再根据首个非环境失败点决定是否进入最小修复。
- 范围控制：live smoke 仍停在环境或脚本入口时，继续冻结阶段 A 代码面；只有进入仓库代码路径后，才处理首个真实失败点。
- 提交判断：当前不提交工作区内未验收的实现改动。当前工作区存在大批跨代码、测试、前端和文档的混合变更，在 Anthropic / Gemini live smoke 仍未闭环前，不满足“小步推进、逐步完成”的提交条件；本轮只提交进度收敛。
- 本轮动作：完成 compile、test、live smoke 复验；结果表明当前无需新增阶段 A 代码或测试补丁。

## 下一步入口

1. 补齐 Anthropic 与 Gemini 的 live smoke 环境变量，或补齐对应 provider profile fallback。
2. 重新执行 `./scripts/run-live-smoke.sh`。
3. 若脚本进入仓库代码路径后仍失败，只处理首个非环境失败点，并补齐对应测试与文档。
