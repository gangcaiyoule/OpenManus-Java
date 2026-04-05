# 开发进度

## 当前阶段状态

- 日期：**2026-04-05**
- 阶段：**阶段 A 验收阻塞收口**
- 状态：**Blocked**
- 提交判断：**本轮只做文档收敛 commit，不做阶段收口 commit**
- 阶段边界：只收口“单 Agent + 上下文治理 A + CodeAct A + 最小工具结果压缩/按需回填”；不进入上下文治理 B/C、MCP 资源融合或 `Multi-Agent` 实现。
- 阶段结论：本轮复核实现与回归后，阶段 A 代码主线仍保持收敛，未发现新的阶段内实现缺口；当前唯一阻塞仍是真实 Provider 验收环境未补齐。

## 当前阻塞

1. 当前唯一有效阻塞仍是 `./scripts/run-live-smoke.sh` 未形成 Anthropic / Gemini 的 non-skipped 验收结果。
2. 当前缺失的真实且非空变量如下：
   - `OPENMANUS_LIVE_ANTHROPIC_MODEL`
   - `OPENMANUS_LIVE_ANTHROPIC_BASE_URL`
   - `OPENMANUS_LIVE_ANTHROPIC_API_KEY`
   - `OPENMANUS_LIVE_GEMINI_MODEL`
   - `OPENMANUS_LIVE_GEMINI_BASE_URL`
   - `OPENMANUS_LIVE_GEMINI_API_KEY`
3. 可接受的补齐入口仍是两类：
   - 直接补齐 `OPENMANUS_LIVE_*`
   - 补齐对应 provider profile：`OPENMANUS_LLM_PROVIDERS_ANTHROPIC_*`、`OPENMANUS_LLM_PROVIDERS_GEMINI_*`

## 当前主线

1. 当前主线只做阶段 A 验收收敛，不新增实现范围。
2. 推进顺序固定为：先补真实 Provider 环境，再跑 `./scripts/run-live-smoke.sh`，再根据新的首个失败点决定是否存在阶段内修正项。
3. 当前工作区虽有大量在途实现，但协调结论不变：先收口验收证据，不以继续铺开代码来替代验收闭环。
4. 在 live smoke 形成完整 non-skipped 证据前，不扩展上下文治理 B/C、MCP 资源融合或 `Multi-Agent` 实现。

## 下一步入口

1. 在当前 shell 或仓库根目录 `.env` 中补齐 Anthropic / Gemini 两组真实且非空配置，或补齐对应 provider profile，且值不能是示例占位值。
2. 重新执行 `./scripts/run-live-smoke.sh`，确认脚本进入 Maven 和测试阶段。
3. 若脚本仍失败，只处理新的首个失败点，并同步收敛 `Review` 与进度文档。
4. 在 live smoke 形成完整 non-skipped 证据前，不新增阶段外实现，不做阶段性 commit。
