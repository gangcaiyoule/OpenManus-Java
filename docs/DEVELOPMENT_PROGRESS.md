# 开发进度

## 当前阶段状态

- 阶段：阶段 A 收口（live smoke 证据闭环）
- 状态：**Blocked（外部门禁未解除）**
- 阶段边界：本阶段只收口“上下文治理 A + CodeAct A”的真实 Provider 验证；任务态上下文、MCP、跨层收敛实现已在工作区推进，但不作为当前阶段切换依据。
- 阶段完成判定：`./scripts/run-live-smoke.sh` 产出 non-skipped 结果，并回填 surefire 四项统计与首个问题分类。
- 当前结论：
  - 关键离线回归已通过，`domain/service -> WorkflowExecutionPort` 端口化收敛已完成。
  - `./scripts/run-live-smoke.sh` 当前输出仍为 `tests=3, failures=0, errors=0, skipped=3`，首个问题分类仍为 `Assumption failed`。
  - 阶段 A 还不能切换；当前唯一验收阻塞仍是真实 Provider 门禁未解除。
- commit 判断：
  - 当前不做阶段完成型 commit。
  - 原因：阶段验收证据未闭环，且工作区存在大批并行代码改动，暂不应以“阶段完成”名义切点。
  - 本轮仅允许提交“文档收敛”类小步 commit。

## 当前阻塞

- 同一会话未注入 OpenAI / Anthropic / Gemini 三组 `OPENMANUS_LIVE_*` 变量。
- 当前 live smoke 三条用例仍全部 skipped，阻塞性质未变化。
- review 侧剩余主问题为 `domain` 包继续吸收 `aiframework runtime` / WebSocket 等运行时细节，且缺少对应的架构守卫。

## 当前主线

1. 先补齐三组 `OPENMANUS_LIVE_*` 变量并在同一会话生效。
2. 执行 `./scripts/run-live-smoke.sh` 验证真实 Provider 链路并读取 surefire 汇总。
3. 若输出转为 non-skipped，再回填本文件与 `docs/Review.md`，并判定阶段 A 是否可结束。
4. 仅在阶段 A 完成后，进入下一阶段主线：做 `domain -> aiframework runtime / WebSocket` 依赖下沉与架构守卫补齐。

## 下一步入口

1. 入口动作：准备并注入三组 `OPENMANUS_LIVE_*` 变量。
2. 验证动作：执行 `./scripts/run-live-smoke.sh`。
3. 收敛动作：把脚本输出中的 surefire 四项统计与首个问题分类回填到本文件和 `docs/Review.md`。
4. 阶段判断：只有在 live smoke 转为 non-skipped 后，才允许切到 `domain -> runtime / WebSocket` 下沉收敛。
