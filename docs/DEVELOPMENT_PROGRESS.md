# 开发进度

## 当前阶段状态

- 日期：**2026-04-05**
- 阶段：**阶段 A 收口**
- 状态：**Blocked**
- 阶段边界：只收口“上下文治理 A + CodeAct A + 最小工具结果压缩/按需回填”的单 Agent 最小链路；不进入上下文治理 B/C、MCP 资源融合或 `Multi-Agent` 实现。
- 当前判断：离线主链路已实测通过；当前阶段唯一硬阻塞仍是真实 Provider live smoke 未闭环。在 live smoke non-skipped 前，不追加新的实现收口项，也不切换阶段。

## 当前阻塞

1. `./scripts/run-live-smoke.sh` 仍是当前阶段唯一真实 Provider 验收入口。
2. `2026-04-05` 实测结果：脚本仍在 Maven 前失败，当前缺少以下 6 个 Anthropic / Gemini live 变量：
   - `OPENMANUS_LIVE_ANTHROPIC_MODEL`
   - `OPENMANUS_LIVE_ANTHROPIC_BASE_URL`
   - `OPENMANUS_LIVE_ANTHROPIC_API_KEY`
   - `OPENMANUS_LIVE_GEMINI_MODEL`
   - `OPENMANUS_LIVE_GEMINI_BASE_URL`
   - `OPENMANUS_LIVE_GEMINI_API_KEY`
3. 在上述真实配置补齐前，仓库内代码和离线测试已无法继续推进当前阶段收口。

## 当前主线

1. 保持阶段 A 单 Agent 收口，不把 `Multi-Agent`、上下文治理 B/C 或 MCP 资源融合带入当前实现边界。
2. 推进顺序固定为：先补齐 Anthropic / Gemini live 配置，再复跑真实 Provider 验收，最后根据 non-skipped 结果判断是否允许阶段 A 收口。
3. 当前只处理验收闭环，不新增实现项、不扩测试口径、不提前讨论下一阶段实现。

## 下一步入口

1. 补配置：
   在当前 shell 或仓库根目录 `.env` 中补齐 Anthropic / Gemini 两组真实且非空的 `OPENMANUS_LIVE_*` 变量，或补齐对应 provider profile 配置。
2. 做验收：
   复跑 `./scripts/run-live-smoke.sh`，确认 OpenAI / Anthropic / Gemini 三条链路均形成 non-skipped 结果。
3. 看首错：
   若 live smoke 仍失败，只处理脚本输出的首个失败点；在此之前不新增阶段 A 实现项，也不进入下一阶段。
