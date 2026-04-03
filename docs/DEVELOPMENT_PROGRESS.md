# 开发进度

## 当前阶段状态

- 阶段：阶段 A 收口（live smoke 证据闭环）
- 状态：**Blocked（外部门禁未解除）**
- 阶段边界：本阶段只收口“上下文治理 A + CodeAct A”的真实 Provider 验证；任务态上下文、MCP、跨层收敛实现已在工作区推进，但不作为当前阶段切换依据。
- 阶段完成判定：`./scripts/run-live-smoke.sh` 产出 non-skipped 结果，并回填 surefire 四项统计与首个问题分类。
- 当前验证结论：
  - `./scripts/mvnw-local.sh -q -DskipITs -Dcheckstyle.skip=true test -Dtest=LiveSmokeScriptIntegrationTest` 已通过。
  - `./scripts/mvnw-local.sh -q -DskipITs -Dcheckstyle.skip=true test -Dtest=SingleAgentArchitectureGuardTest,AgentServiceConversationMemoryTest,WorkflowStreamServiceSessionMemoryTest,TaskStateContextInjectorTest,AbstractAgentExecutorTaskStateIntegrationTest,McpToolRegistryBootstrapTest,AiRuntimeConfigRuntimeWiringTest,RuntimeSandboxAdaptersTest` 已通过。
  - `./scripts/run-live-smoke.sh` 当前输出：`tests=3, failures=0, errors=0, skipped=3`。
  - 当前首个问题分类：`Assumption failed`。
- 当前判断：离线回归和 live smoke 汇总脚本链路已可使用，但真实 Provider 证据仍未闭环；当前不进入新的能力扩张，只做门禁解除、验收回填和下一阶段入口准备。

## 当前阻塞

- 同一会话未注入 OpenAI / Anthropic / Gemini 三组 `OPENMANUS_LIVE_*` 变量。
- 当前 live smoke 三条用例仍全部 skipped，阻塞性质未变化。
- 仓库当前 `./scripts/mvnw-local.sh -q -DskipITs test -Dtest=LiveSmokeScriptIntegrationTest` 会被既有 checkstyle 结果解析异常拦截；本轮已用 `-Dcheckstyle.skip=true` 完成脚本链路验证，但该问题不属于当前阶段目标本身。
- review 侧已收敛出下一阶段的两项主问题：`domain/service` 直接依赖 `UnifiedWorkflow`，以及 `domain` 包继续吸收 `aiframework runtime` / WebSocket 等运行时细节；当前阶段只做记录，不在 live smoke 门禁未解除前展开改造。

## 当前主线

1. 先补齐三组 `OPENMANUS_LIVE_*` 变量并在同一会话生效。
2. 执行 `./scripts/run-live-smoke.sh` 验证真实 Provider 链路并读取 surefire 汇总。
3. 若输出转为 non-skipped，再回填本文件与 `docs/Review.md`，并判定阶段 A 是否可结束。
4. 并行清理既有 checkstyle 结果解析异常，恢复不跳过 checkstyle 的常规测试入口，但不影响阶段 A 是否结束的判定。
5. 仅在阶段 A 完成后，才进入下一阶段主线：先做 `domain/service -> UnifiedWorkflow` 的最小端口化收敛，再做 `domain -> aiframework runtime` 依赖下沉与架构守卫补齐。

## 下一步入口

1. 入口动作：准备并注入三组 `OPENMANUS_LIVE_*` 变量。
2. 验证动作：运行 `./scripts/run-live-smoke.sh`。
3. 收敛动作：按脚本输出回填 surefire 统计与首个问题分类；若仍为 skipped，则继续停留在阶段 A 收口，不切到端口化改造。
4. 工程动作：修复 checkstyle 结果解析异常后，复跑不带 `-Dcheckstyle.skip=true` 的常规测试入口。
