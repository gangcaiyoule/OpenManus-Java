# 开发进度

## 当前阶段状态

- 日期：**2026-04-06**
- 阶段：**阶段 A 验收收口**
- 状态：**Blocked**
- 阶段边界：只收口“单 Agent + 上下文治理 A + CodeAct A + 最小工具结果压缩/按需回填”；不进入上下文治理 B/C、MCP 资源融合或 `Multi-Agent` 默认实现面。
- 当前离线验证状态：`./scripts/mvnw-local.sh -q -DskipTests compile` 与 `./scripts/mvnw-local.sh -q -DskipITs test` 已通过。
- 当前协调结论：阶段 A 代码面不再扩展，当前唯一未闭环项仍是真实 Provider live smoke 验收。

## 当前阻塞

- `./scripts/run-live-smoke.sh` 仍未闭环，首个失败点仍是 Maven 启动前的环境校验，不是仓库内实现失败。
- 当前缺少 Anthropic / Gemini 真实 Provider 的有效环境变量：
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

## 当前主线

- 不新增任何阶段 A 之外的实现或验证面。
- 不再为当前阻塞补写仓库内替代逻辑或绕过校验。
- 只围绕真实 Provider live smoke 验收补齐环境并完成 non-skipped 结果。
- 若 live smoke 继续失败，只处理脚本输出的首个失败点。

## 下一步入口

1. 在当前 shell 或仓库根目录 `.env` 中补齐 Anthropic / Gemini 两组真实且非空的 `OPENMANUS_LIVE_*` 变量，或补齐对应 provider profile fallback。
2. 重新执行 `./scripts/run-live-smoke.sh`，确认 OpenAI / Anthropic / Gemini 三条链路均形成 non-skipped 结果。
3. 若仍失败，按首个失败点继续收敛，并同步更新 `docs/DEVELOPMENT_PROGRESS.md` 与 `docs/Review.md`。
