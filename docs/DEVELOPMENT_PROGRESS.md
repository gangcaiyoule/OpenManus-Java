# 开发进度

## 当前阶段状态

- 日期：**2026-04-06**
- 阶段：**阶段 A 验收收口**
- 状态：**Blocked**
- 阶段边界：只收口“单 Agent + 上下文治理 A + CodeAct A + 最小工具结果压缩/按需回填”。
- 本轮复验：`./scripts/mvnw-local.sh -q -DskipTests compile` 通过；`./scripts/mvnw-local.sh -q -DskipITs test` 通过；`./scripts/run-live-smoke.sh` 在 Maven 启动前因 Anthropic / Gemini 环境变量缺失失败。
- 当前结论：仓库内实现、分层边界与离线测试已收口，当前不再扩展阶段 A 代码面；唯一未闭环项仍是真实 Provider live smoke 验收。

## 当前主线

- 不再扩展阶段 A 代码面。
- 只围绕真实 Provider live smoke 验收补齐环境并完成 non-skipped 结果。
- 若 live smoke 继续失败，只处理脚本输出的首个失败点。

## 当前阻塞

- 缺少 Anthropic / Gemini 真实 Provider 的有效 live smoke 环境变量：
  - `OPENMANUS_LIVE_ANTHROPIC_MODEL`
  - `OPENMANUS_LIVE_ANTHROPIC_BASE_URL`
  - `OPENMANUS_LIVE_ANTHROPIC_API_KEY`
  - `OPENMANUS_LIVE_GEMINI_MODEL`
  - `OPENMANUS_LIVE_GEMINI_BASE_URL`
  - `OPENMANUS_LIVE_GEMINI_API_KEY`
- 对应 provider profile fallback 当前也不可用：
  - `OPENMANUS_LLM_PROVIDERS_ANTHROPIC_MODEL`
  - `OPENMANUS_LLM_PROVIDERS_ANTHROPIC_BASE_URL`
  - `OPENMANUS_LLM_PROVIDERS_ANTHROPIC_API_KEY`
  - `OPENMANUS_LLM_PROVIDERS_GEMINI_MODEL`
  - `OPENMANUS_LLM_PROVIDERS_GEMINI_BASE_URL`
  - `OPENMANUS_LLM_PROVIDERS_GEMINI_API_KEY`

## 下一步入口

1. 在当前 shell 或仓库根目录 `.env` 中补齐 Anthropic / Gemini 两组真实且非空的 `OPENMANUS_LIVE_*` 变量，或补齐对应 provider profile fallback。
2. 重新执行 `./scripts/run-live-smoke.sh`，确认 OpenAI / Anthropic / Gemini 三条链路均形成 non-skipped 结果。
3. 若仍失败，只处理脚本输出的首个失败点，并继续保持不扩展阶段 A 范围。
