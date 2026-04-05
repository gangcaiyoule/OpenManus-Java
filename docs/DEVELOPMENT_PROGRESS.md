# 开发进度

## 当前阶段状态

- 日期：**2026-04-06**
- 阶段：**阶段 A 验收收口**
- 状态：**Blocked**
- 阶段边界：只收口“单 Agent + 上下文治理 A + CodeAct A + 最小工具结果压缩/按需回填”。
- 当前结论：当前实现、分层和离线验证仍满足阶段 A 要求；当前唯一未闭环项仍是真实 Provider live smoke 验收。
- 本轮阶段结论：继续冻结阶段 A 代码面；在 live smoke 进入仓库代码路径前，不新增阶段 A 修复项。

## 当前阻塞

- 缺少 `OPENMANUS_LIVE_ANTHROPIC_MODEL`、`OPENMANUS_LIVE_ANTHROPIC_BASE_URL`、`OPENMANUS_LIVE_ANTHROPIC_API_KEY`。
- 缺少 `OPENMANUS_LIVE_GEMINI_MODEL`、`OPENMANUS_LIVE_GEMINI_BASE_URL`、`OPENMANUS_LIVE_GEMINI_API_KEY`。
- 对应 `OPENMANUS_LLM_PROVIDERS_ANTHROPIC_*` 与 `OPENMANUS_LLM_PROVIDERS_GEMINI_*` fallback 当前也无可用值。
- 在 OpenAI、Anthropic、Gemini 三条链路都产出 non-skipped 结果前，阶段 A 不能收口。

## 当前主线

- 主线目标：只完成阶段 A 验收收口，不扩展上下文治理 B/C、MCP 资源融合或 `Multi-Agent` 默认实现面。
- 当前验证结论：`./scripts/mvnw-local.sh -q -DskipTests compile` 通过，`./scripts/mvnw-local.sh -q -DskipITs test` 通过，`./scripts/run-live-smoke.sh` 仍在环境校验阶段失败。
- 推进顺序：先补齐 Anthropic / Gemini live smoke 环境，再复跑统一验收脚本，再根据首个非环境失败点决定是否进入最小修复。
- 范围控制：live smoke 未进入仓库代码路径前，继续冻结阶段 A 代码面；只有出现首个真实运行失败点，才进入对应最小补丁、测试和文档更新。
- 提交判断：当前仓库存在大量并行中的混合改动，不适合对阶段 A 实现面做统一提交；本轮只提交协调收敛后的 `docs/DEVELOPMENT_PROGRESS.md`。

## 下一步入口

1. 补齐 Anthropic 与 Gemini 的 live smoke 环境变量，或补齐对应 provider profile fallback。
2. 重新执行 `./scripts/run-live-smoke.sh`，确认三条链路都进入 non-skipped 路径。
3. 若脚本进入仓库代码路径后仍失败，只处理首个非环境失败点，并补齐对应测试与文档。
4. 若三条 live smoke 都产出 non-skipped 结果，再执行阶段 A 最终收口判断。
