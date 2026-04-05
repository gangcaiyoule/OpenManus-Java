# 开发进度

## 当前阶段状态

- 日期：**2026-04-06**
- 阶段：**阶段 A 验收收口**
- 状态：**Blocked**
- 阶段边界：只收口“单 Agent + 上下文治理 A + CodeAct A + 最小工具结果压缩/按需回填”。
- 当前结论：实现、分层与离线验证仍符合阶段 A 边界；唯一未闭环项仍是真实 Provider live smoke 验收。
- 当前验证：`./scripts/mvnw-local.sh -q -DskipTests compile` 与 `./scripts/mvnw-local.sh -q -DskipITs test` 已通过；`./scripts/run-live-smoke.sh` 仍停在 Anthropic / Gemini 环境变量校验，尚未进入仓库代码路径。
- 提交判断：本轮适合只提交进度文档收敛，不做新的阶段 A 代码结论提交。

## 当前阻塞

- 缺少 `OPENMANUS_LIVE_ANTHROPIC_MODEL`、`OPENMANUS_LIVE_ANTHROPIC_BASE_URL`、`OPENMANUS_LIVE_ANTHROPIC_API_KEY`。
- 缺少 `OPENMANUS_LIVE_GEMINI_MODEL`、`OPENMANUS_LIVE_GEMINI_BASE_URL`、`OPENMANUS_LIVE_GEMINI_API_KEY`。
- 对应 `OPENMANUS_LLM_PROVIDERS_ANTHROPIC_*` 与 `OPENMANUS_LLM_PROVIDERS_GEMINI_*` fallback 当前也没有可用值。
- 在 OpenAI、Anthropic、Gemini 三条链路都产出 non-skipped 结果前，阶段 A 不能收口。

## 当前主线

- 主线目标：只完成阶段 A 验收收口，不再扩展上下文治理、CodeAct、MCP 或 Multi-Agent 实现面。
- 推进顺序：先补齐 Anthropic / Gemini live smoke 环境，再复跑统一验收脚本，再根据首个非环境失败点决定是否进入最小修复。
- 范围控制：若 live smoke 仍停在环境或脚本入口，则保持当前代码冻结；只有进入仓库代码路径后，才允许处理首个真实失败点。

## 下一步入口

1. 在当前 shell 或仓库根目录 `.env` 中补齐 Anthropic 与 Gemini 的 live smoke 变量，或补齐对应 provider profile fallback。
2. 重新执行 `./scripts/run-live-smoke.sh`。
3. 若脚本进入仓库代码路径后仍失败，只处理首个失败点，并补齐对应测试与文档，不扩展阶段 A 代码面。
