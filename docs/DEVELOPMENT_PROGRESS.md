# 开发进度

## 当前阶段状态

- 阶段：阶段 A 收口（live smoke 证据闭环）
- 状态：**Blocked（外部门禁未解除）**
- 阶段完成判定：`./scripts/run-live-smoke.sh` 产出 non-skipped 结果，并回填 surefire 四项统计与首个问题分类。
- 当前验证结论：
  - `./scripts/mvnw-local.sh -q -DskipITs test` 已通过。
  - `./scripts/run-live-smoke.sh` 当前输出：`tests=3, failures=0, errors=0, skipped=3`。
  - 当前首个问题分类：`Assumption failed`。
- 当前判断：离线回归稳定，但真实 Provider 证据仍未闭环；任务态上下文、MCP 与跨层收敛相关在途实现不作为当前阶段切换依据。

## 当前阻塞

- 同一会话未注入 OpenAI / Anthropic / Gemini 三组 `OPENMANUS_LIVE_*` 变量。
- 当前 live smoke 三条用例仍全部 skipped，阻塞性质未变化。

## 当前主线

1. 先补齐三组 `OPENMANUS_LIVE_*` 变量并在同一会话生效。
2. 执行 `./scripts/run-live-smoke.sh` 验证真实 Provider 链路并读取 surefire 汇总。
3. 若输出转为 non-skipped，再回填本文件与 `docs/Review.md`。
4. 完成阶段 A 证据闭环后，再推进 `domain/service -> UnifiedWorkflow` 的最小端口化收敛。

## 下一步入口

1. 入口动作：准备并注入三组 `OPENMANUS_LIVE_*` 变量。
2. 验证动作：运行 `./scripts/run-live-smoke.sh`。
3. 收敛动作：按脚本输出回填 surefire 统计与首个问题分类；若仍为 skipped，则继续停留在阶段 A 收口。
