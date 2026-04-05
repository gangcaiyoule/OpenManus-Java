# 开发进度

## 当前阶段状态

- 日期：**2026-04-05**
- 阶段：**阶段 A 收口**
- 状态：**Blocked**
- 阶段边界：只收口“上下文治理 A + CodeAct A + 最小工具结果压缩/按需回填”的单 Agent 最小链路；不进入上下文治理 B/C、MCP 资源融合或 `Multi-Agent` 实现。
- 当前判断：离线主链路已通过回归验证，当前不能切阶段的唯一硬门槛仍是真实 Provider live smoke 未闭环；分层守卫补强属于 live smoke 闭环后的阶段 A 收口项，不提前并行扩张。

## 当前阻塞

1. `./scripts/run-live-smoke.sh` 仍是当前阶段唯一真实 Provider 验收入口；`2026-04-05` 实测仍在 Maven 前失败，当前缺少以下 6 个 Anthropic / Gemini live 变量：
   - `OPENMANUS_LIVE_ANTHROPIC_MODEL`
   - `OPENMANUS_LIVE_ANTHROPIC_BASE_URL`
   - `OPENMANUS_LIVE_ANTHROPIC_API_KEY`
   - `OPENMANUS_LIVE_GEMINI_MODEL`
   - `OPENMANUS_LIVE_GEMINI_BASE_URL`
   - `OPENMANUS_LIVE_GEMINI_API_KEY`
2. 分层守卫当前仍主要依赖源码字符串扫描；在真实 Provider 验收闭环前，该问题只作为下一收口项保留，不单独扩成新阶段。

## 当前主线

1. 保持阶段 A 单 Agent 收口，不把 `Multi-Agent`、上下文治理 B/C 或 MCP 资源融合带入当前实现边界。
2. 开发顺序固定为：先补齐 Anthropic / Gemini live 配置并打通 `run-live-smoke.sh`，再补强分层守卫，最后再判断阶段 A 是否满足收口条件。
3. `2026-04-05` 已重新验证：
   - `./scripts/mvnw-local.sh -q -DskipITs test` 通过。
   - `cd frontend && npm test -- --run` 通过。
   - `cd frontend && npm run build` 通过。
   - `./scripts/run-live-smoke.sh` 在 Maven 前因缺少 Anthropic / Gemini live 变量失败。

## 下一步入口

1. 在当前 shell 或仓库根目录 `.env` 中补齐 Anthropic / Gemini 两组真实且非空的 `OPENMANUS_LIVE_*` 变量，或补齐对应 provider profile 配置。
2. 复跑 `./scripts/run-live-smoke.sh`；若仍失败，只处理脚本输出的首个失败点。
3. live smoke non-skipped 后，补 fully-qualified 跨层引用与无 import 直接引用两类守卫覆盖。
4. 仅在上述两项完成后，再复核是否允许结束阶段 A；在此之前不扩实现范围。
