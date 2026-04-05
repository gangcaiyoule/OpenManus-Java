# 开发进度

## 当前阶段状态

- 日期：**2026-04-05**
- 阶段：**阶段 A 验收前阻塞**
- 状态：**Blocked**
- 提交判断：**本轮不做阶段 A 收口 commit；仅在更新协调文档后单独提交文档收敛结果**
- 阶段边界：只收口“单 Agent + 上下文治理 A + CodeAct A + 最小工具结果压缩/按需回填”；不进入上下文治理 B/C、MCP 资源融合或 `Multi-Agent` 实现。
- 当前结论：离线编译与阶段内关键回归已通过，当前没有新的阶段内代码缺口；唯一未闭环项仍是真实 Provider 验收。

## 当前阻塞

1. `./scripts/run-live-smoke.sh` 仍是当前阶段唯一真实 Provider 验收入口。
2. `2026-04-05` 实测结果：脚本仍在 Maven 前失败，当前缺少以下 6 个 Anthropic / Gemini live 变量：
   - `OPENMANUS_LIVE_ANTHROPIC_MODEL`
   - `OPENMANUS_LIVE_ANTHROPIC_BASE_URL`
   - `OPENMANUS_LIVE_ANTHROPIC_API_KEY`
   - `OPENMANUS_LIVE_GEMINI_MODEL`
   - `OPENMANUS_LIVE_GEMINI_BASE_URL`
   - `OPENMANUS_LIVE_GEMINI_API_KEY`
3. 在上述真实配置补齐前，离线通过结果不能替代阶段 A 收口证据。
4. 当前阻塞属于环境配置缺失，不属于新的仓库内阶段 A 代码问题。

## 当前主线

1. 只围绕阶段 A 验收闭环推进，不把 `Multi-Agent`、上下文治理 B/C 或 MCP 资源融合带入当前轮。
2. 当前任务顺序固定为：
   - 先补齐 Anthropic / Gemini live 配置或对应 provider profile。
   - 再复跑 `./scripts/run-live-smoke.sh` 形成 non-skipped 验收结果。
   - 若仍失败，只处理脚本输出的首个失败点，并确认该问题是否仍落在阶段 A 边界内。
3. 在真实 Provider 验收通过前，不做阶段收口判断，不合并阶段外改动，不扩大实现范围。

## 下一步入口

1. 在当前 shell 或仓库根目录 `.env` 中补齐 Anthropic / Gemini 两组真实且非空的 `OPENMANUS_LIVE_*` 变量，或补齐对应 `OPENMANUS_LLM_PROVIDERS_ANTHROPIC_*`、`OPENMANUS_LLM_PROVIDERS_GEMINI_*` 配置。
2. 复跑 `./scripts/run-live-smoke.sh`，确认 OpenAI / Anthropic / Gemini 三条链路均形成 non-skipped 结果。
3. 若 live smoke 仍失败，只围绕首个失败点做阶段 A 内的最小修正。
4. 若 live smoke 通过，再单独判断是否进行阶段 A 收口 commit。
