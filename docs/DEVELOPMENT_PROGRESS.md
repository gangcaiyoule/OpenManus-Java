# 开发进度

## 当前阶段状态

- 日期：**2026-04-05**
- 阶段：**阶段 A 验收阻塞收敛**
- 状态：**Blocked**
- 提交判断：**当前不做阶段收口 commit；本轮如需同步协调结论，只允许独立文档 commit，且不得混入在途实现变更**
- 阶段边界：只收口“单 Agent + 上下文治理 A + CodeAct A + 最小工具结果压缩/按需回填”；不进入上下文治理 B/C、MCP 资源融合或 `Multi-Agent` 实现。

## 当前验证结果

1. `./scripts/mvnw-local.sh -q -DskipTests compile` 已通过。
2. `./scripts/mvnw-local.sh -q -DskipITs test` 已通过。
3. `cd frontend && npm test -- --run` 已通过，`17` 个测试全部通过。
4. `cd frontend && npm run build` 已通过。
5. `2026-04-05` 本轮复跑 `./scripts/run-live-smoke.sh` 仍在 Maven 前失败，首个失败点仍仅是 Anthropic / Gemini live 环境变量缺失。
6. `2026-04-05` reviewer 结合文档约束与本地回归复核 `agent/context`、`agent/base`、`domain/service`、`infra/config`、`infra/workflow`、`infra/web` 与 `aiframework/tool/mcp` 后，未发现新的阶段边界外扩张、分层回退、过度抽象或离线测试口径遗漏。
7. 当前工作区仍存在大量在途实现变更；这些变更尚未形成 live smoke non-skipped 验收证据，本轮协调不将其纳入提交范围。

## 当前阻塞

1. 当前阶段唯一验收入口仍是 `./scripts/run-live-smoke.sh`。
2. `2026-04-05` 实测缺少以下 6 个真实且非空的 live 变量：
   - `OPENMANUS_LIVE_ANTHROPIC_MODEL`
   - `OPENMANUS_LIVE_ANTHROPIC_BASE_URL`
   - `OPENMANUS_LIVE_ANTHROPIC_API_KEY`
   - `OPENMANUS_LIVE_GEMINI_MODEL`
   - `OPENMANUS_LIVE_GEMINI_BASE_URL`
   - `OPENMANUS_LIVE_GEMINI_API_KEY`
3. 在上述配置补齐前，Anthropic / Gemini 无法形成 non-skipped 验收结果，阶段 A 不能收口。
4. 当前阻塞属于环境配置缺失；在现有离线验证全部通过的前提下，继续扩展仓库代码不能直接解除该阻塞。
5. 当前工作区仍有大量未收口实现改动；在真实 Provider 验收闭环前，不对这些改动作阶段收口判断，也不混入本轮协调提交。

## 当前主线

1. 当前主线只做阶段 A 验收收敛，不新增实现范围；`2026-04-05` 开发侧复核未发现新的阶段 A 代码级首要缺口，先解除环境阻塞。
2. 执行顺序固定为：
   1. 补齐 Anthropic / Gemini 真实 Provider 配置。
   2. 复跑 `./scripts/run-live-smoke.sh`。
   3. 若仍失败，只处理脚本输出的首个失败点，且该修正必须仍落在阶段 A 边界内。
   4. 仅在 live smoke 全部 non-skipped 后，才进入阶段 A 收口判断与收口 commit。
3. 在 live smoke 全部 non-skipped 前，不做阶段收口 commit，也不整理或混入当前工作区尚未验收的实现改动。
4. 本轮协调输出只保留阶段状态、当前阻塞、当前主线和下一步入口，不把过程性说明继续累积到进度文档。

## 下一步入口

1. 在当前 shell 或仓库根目录 `.env` 中补齐 Anthropic / Gemini 两组真实且非空的 `OPENMANUS_LIVE_*` 变量，或补齐对应 `OPENMANUS_LLM_PROVIDERS_ANTHROPIC_*`、`OPENMANUS_LLM_PROVIDERS_GEMINI_*` 配置。
2. 重新执行 `./scripts/run-live-smoke.sh`；当前首个失败点仍是上述 6 个变量缺失，未出现新的代码级首要阻塞。
3. 若 live smoke 进入 Maven/测试阶段后仍失败，只跟进新的首个失败点，不并行展开多个修复支线。
4. 在真实 Provider 验收通过前，不做阶段 A 收口 commit；当前仅允许提交文档同步结果。
