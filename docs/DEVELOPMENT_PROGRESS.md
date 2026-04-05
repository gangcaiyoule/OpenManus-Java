# 开发进度

## 当前阶段状态

- 日期：**2026-04-05**
- 阶段：**阶段 A 验收阻塞收敛**
- 状态：**Blocked**
- 提交判断：**当前不做阶段收口 commit；先完成 live smoke non-skipped 验收，再判断是否进入收口提交**
- 阶段边界：只收口“单 Agent + 上下文治理 A + CodeAct A + 最小工具结果压缩/按需回填”；不进入上下文治理 B/C、MCP 资源融合或 `Multi-Agent` 实现。
- 当前结果：
  - `./scripts/mvnw-local.sh -q -DskipTests compile` 通过。
  - `./scripts/mvnw-local.sh -q -DskipITs test` 通过。
  - `cd frontend && npm test -- --run` 通过，共 `17` 个测试。
  - `cd frontend && npm run build` 通过。
  - `./scripts/run-live-smoke.sh` 仍在 Maven 启动前失败，首个失败点未变化。
  - review 复核 `agent/context`、`agent/base`、`domain/service`、`infra/config`、`infra/workflow` 与 `aiframework/tool/mcp` 后，当前未发现新的阶段外扩张、分层回退或离线可复现回归。

## 当前阻塞

1. 当前阶段唯一未闭环的验收入口仍是 `./scripts/run-live-smoke.sh`。
2. `2026-04-05` 实测仍缺少以下 6 个真实且非空的 live 变量：
   - `OPENMANUS_LIVE_ANTHROPIC_MODEL`
   - `OPENMANUS_LIVE_ANTHROPIC_BASE_URL`
   - `OPENMANUS_LIVE_ANTHROPIC_API_KEY`
   - `OPENMANUS_LIVE_GEMINI_MODEL`
   - `OPENMANUS_LIVE_GEMINI_BASE_URL`
   - `OPENMANUS_LIVE_GEMINI_API_KEY`
3. 在上述配置补齐前，Anthropic / Gemini 无法形成 non-skipped 验收结果，阶段 A 不能收口。
4. 当前阻塞属于环境配置缺失，不能通过继续修改仓库代码直接解除。

## 当前主线

1. 当前主线只做阶段 A 验收收敛，不新增实现范围。
2. 当前有效结论是：离线编译、后端回归、前端单测与前端构建已通过，但测试覆盖仍缺 Anthropic / Gemini 真实 Provider non-skipped 证据。
3. 在 live smoke 形成完整 non-skipped 证据前，不扩展上下文治理 B/C、MCP 资源融合或 `Multi-Agent` 实现。

## 下一步入口

1. 在当前 shell 或仓库根目录 `.env` 中补齐 Anthropic / Gemini 两组真实且非空的 `OPENMANUS_LIVE_*` 变量，或补齐对应 `OPENMANUS_LLM_PROVIDERS_ANTHROPIC_*`、`OPENMANUS_LLM_PROVIDERS_GEMINI_*` 配置。
2. 重新执行 `./scripts/run-live-smoke.sh`。
3. 若 live smoke 进入 Maven/测试阶段后仍失败，只跟进新的首个失败点，且修正范围仍限制在阶段 A。
