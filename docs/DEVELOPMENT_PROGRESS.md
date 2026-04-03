# 开发进度

## 当前阶段状态

- 阶段：阶段 A 收口
- 状态：**Blocked**
- 阶段边界：只收口“上下文治理 A + CodeAct A”的真实 Provider 验收，不扩展到任务态上下文增强、MCP 资源融合或额外重构。
- 完成判定：`./scripts/run-live-smoke.sh` 产出 non-skipped 结果，并回填 `tests / failures / errors / skipped` 与首个问题分类。
- 当前判定：2026-04-03 复核 `./scripts/run-live-smoke.sh` 仍为 `tests=3, failures=0, errors=0, skipped=3`，首个问题分类为 `Assumption failed`，因此阶段 A 不能切换。

## 当前阻塞

- 同一会话未注入 OpenAI / Anthropic / Gemini 三组 `OPENMANUS_LIVE_*` 环境变量。
- 真实 Provider 验收仍全部 skipped，缺少 non-skipped 证据。
- `AbstractAgentExecutor` 已默认注入最小任务态卡片，当前只能按“阶段 A 允许的辅助上下文”收口，不能继续向阶段 C 扩张。

## 当前主线

1. 保持阶段 A 验收口径不变，只推进真实 Provider 的 non-skipped 证据闭环。
2. 任务态上下文当前仅按最小卡片注入视为阶段 A 的辅助治理实现，不追加字段、策略或额外抽象。
3. `WebProxyController` 职责偏重记为后续收口项，但不插队到当前主线之前。

## 下一步入口

1. 在同一会话注入 OpenAI / Anthropic / Gemini 所需 `OPENMANUS_LIVE_*` 环境变量。
2. 复跑 `./scripts/run-live-smoke.sh`，记录 `tests / failures / errors / skipped` 与首个问题分类。
3. 若结果 non-skipped，则更新阶段 A 完成结论；若失败，则只沿脚本输出收敛到具体 Provider 或传输层问题。
