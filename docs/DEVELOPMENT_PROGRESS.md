# 开发进度

## 当前阶段状态

- 日期：**2026-04-05**
- 阶段：**阶段 A 收口**
- 状态：**Blocked**
- 阶段边界：只收口“上下文治理 A + CodeAct A + 最小工具结果压缩/按需回填”的单 Agent 最小链路；不进入上下文治理 B/C、MCP 资源融合或 `Multi-Agent` 实现。
- 当前判断：主线实现仍围绕单 Agent 阶段 A 收口，`domain -> port -> infra adapter` 与 `WebProxy` 最小安全边界已基本对齐；当前阻塞集中在真实 Provider 验收未闭环，分层守卫增强只能排在其后。
- commit 判断：**代码主线当前不 commit；本轮可单独 commit 协调文档。** 原因是阶段 A 仍缺真实 Provider non-skipped 验收，但文档已经形成新的当前状态与下一步入口，适合独立留痕。

## 当前阻塞

1. `./scripts/run-live-smoke.sh` 仍是当前阶段唯一真实 Provider 验收入口；`2026-04-05` 实测仍在 Maven 前失败，当前缺少以下 6 个 Anthropic / Gemini live 变量：
   - `OPENMANUS_LIVE_ANTHROPIC_MODEL`
   - `OPENMANUS_LIVE_ANTHROPIC_BASE_URL`
   - `OPENMANUS_LIVE_ANTHROPIC_API_KEY`
   - `OPENMANUS_LIVE_GEMINI_MODEL`
   - `OPENMANUS_LIVE_GEMINI_BASE_URL`
   - `OPENMANUS_LIVE_GEMINI_API_KEY`
2. 架构守卫测试已能挡住部分显式 import 回退，但当前仍主要依赖源码字符串扫描；该项属于阶段 A 收口增强，不覆盖真实 Provider 验收阻塞。

## 当前主线

1. 保持阶段 A 单 Agent 收口，不把 `Multi-Agent`、上下文治理 B/C 或 MCP 资源融合带入当前实现边界。
2. 先以真实 Provider live smoke 闭环作为阶段切换前置条件，其他增强项不抢主线优先级。
3. 已确认当前最小主线仍成立：
   - `domain -> port -> infra adapter` 分层方向未偏离。
   - `WebProxy` base64url 输入约束、目标地址校验和重定向二次校验已经到位。
4. `2026-04-05` 已重新验证：
   - `./scripts/mvnw-local.sh -q -DskipITs -Dtest=SingleAgentArchitectureGuardTest,Step2AbstractAgentExecutorBuilderRuntimeApiGuardTest,WebProxyControllerTest,WebProxyServiceTest,HttpUrlConnectionWebProxyAdapterTest,AgentServiceMonitoringIntegrationTest,WorkflowStreamServiceMonitoringIntegrationTest,WorkflowExecutionEventPortAdapterTest,SessionSandboxManagerLifecycleTest,LiveSmokeEnvTest test`
   - `cd frontend && npm test -- --run`
   - `cd frontend && npm run build`
   - `./scripts/run-live-smoke.sh`

## 下一步入口

1. 在当前 shell 或仓库根目录 `.env` 中补齐 Anthropic / Gemini 两组真实且非空的 `OPENMANUS_LIVE_*` 变量，或补齐对应 provider profile 配置。
2. 复跑 `./scripts/run-live-smoke.sh`；若仍失败，只处理脚本输出的首个失败点。
3. live smoke 闭环后，再补 fully-qualified usage 和无 import 直接引用路径的架构守卫增强，避免分层约束继续退化。
