# 技术实现方案

## 1. 当前适用范围

本文档仅覆盖当前阶段已经落地或正在收口的最小方案：

1. 上下文治理阶段 A。
2. CodeAct 阶段 A。
3. 工具结果压缩与按需回填的最小链路。

当前不覆盖：

- 上下文治理阶段 B/C 的复杂摘要与任务态能力。
- MCP client、MCP 工具接入与资源融合。
- 为未来扩展提前引入的新框架或大规模重构。

补充边界说明：

- 当前工作区已出现任务态上下文与 MCP 相关实现，但这些内容不作为本阶段验收项。
- 阶段是否切换，仍只以 live smoke non-skipped 证据和当前最小链路收口情况为准。

## 2. 分层约束

遵循 `AGENTS.md`：

- `domain`：承载核心业务语义，不直接吸收执行框架细节。
- `agent`：承载 Agent 执行编排、上下文治理与回填逻辑。
- `infra`：只负责配置、存储、外部适配，不承载业务编排。
- `aiframework`：提供模型调用、消息结构与运行时抽象。

当前实现继续遵守“先最小可运行，再逐步增强”的原则。

## 3. 当前最小实现

### 3.1 上下文治理

目标：在模型调用前统一处理上下文，而不是把规则散落在执行器中。

当前最小链路：

- `ContextSnapshot`：提取历史消息、当前轮消息和当前用户消息。
- `ContextBudgetPolicy`：统一消息数量与近似 token 预算。
- `ContextAssembler`：按统一顺序完成上下文组装与裁剪。

规则保持简单可回归：

1. 先裁剪历史消息。
2. 再拼接当前轮消息。
3. 最后执行总量与近似 token 预算。

### 3.2 CodeAct 最小闭环

目标：先保证“计划 -> 工具执行 -> 观察结果 -> 继续执行”的最小回路成立。

当前闭环：

1. 模型输出工具调用。
2. 执行本地注册工具。
3. 将工具结果统一写回消息链。
4. 进入下一轮执行。

基础保护：

- 空输入快速失败。
- 最大轮次保护。
- 最大执行时长保护。
- 未知工具重复调用保护。

### 3.3 工具结果压缩与按需回填

目标：先控制上下文体积，再保留最小可追溯能力。

当前最小策略：

- 超长 `TOOL` 消息进行压缩，保留必要元信息与头尾预览。
- 回填只依赖显式信号：
  - 用户显式给出 `artifactId`
  - 用户显式提及工具名
- 回填后仍进入统一预算治理链路，避免打爆上下文窗口。

### 3.4 Live smoke 证据闭环

目标：把真实 Provider 验证与 surefire 结果汇总收敛到单一入口，避免手工翻报告。

当前最小链路：

1. `scripts/run-live-smoke.sh` 固定通过 `scripts/mvnw-local.sh` 执行 `*LiveSmokeTest`。
2. 测试结束后读取 `target/surefire-reports/TEST-*LiveSmokeTest.xml`。
3. 汇总 `tests/failures/errors/skipped`，并输出首个问题分类。
4. 若存在 `failure/error/skipped` 或缺少报告，脚本直接失败。

当前分类规则保持简单：

- 优先识别 `failure`。
- 其次识别 `error`。
- 再识别 `skipped`，其中包含 `Assumption failed` 时输出该分类。
- 全部 non-skipped 时输出 `first issue: none`。

## 4. 当前测试口径

围绕“主流程 + 分支 + 异常 + 边界”进行覆盖：

- 上下文快照、预算与组装测试。
- 执行器单轮 / 多轮执行测试。
- 工具结果压缩与回填测试。
- 配置装配与运行时守卫测试。
- live smoke 统一通过 `scripts/run-live-smoke.sh` 进入；其内部仍只使用 `scripts/mvnw-local.sh` 作为 Maven 入口，并在执行后汇总 surefire 结果。
- `LiveSmokeScriptIntegrationTest` 覆盖 live smoke 汇总脚本的成功、跳过、缺报告分支。

当前结论：

- 离线回归可通过。
- live smoke 汇总入口已存在并可稳定输出 surefire 统计。
- 当前仍受 `OPENMANUS_LIVE_*` 环境变量门禁影响，尚未形成 non-skipped 证据。

## 5. 已知问题

### 5.1 外部门禁未闭环

- `scripts/mvnw-local.sh` 已可收口本地 `JAVA_HOME` 前置条件。
- `scripts/run-live-smoke.sh` 已可自动回填 live smoke 统计与首个问题分类。
- 当前仍缺少三组 `OPENMANUS_LIVE_*` 变量，导致 live smoke 全部 skipped。

### 5.2 `domain -> agent` 依赖仍需收敛

- `domain/service` 仍直接依赖 `UnifiedWorkflow` 具体实现。
- 后续应通过“最小端口 + agent 适配 + 守卫测试”收敛。

## 6. 下一步实现顺序

1. 先完成 live smoke non-skipped 证据闭环。
2. 再推进 `domain/service -> UnifiedWorkflow` 的最小端口化改造。
3. 在上述稳定后，再讨论上下文增强或 MCP 接入。

## 7. 维护约定

- 本文档只保留当前有效技术方案。
- 已完成但不再影响决策的历史切片不再逐轮堆积。
- 方案变更时直接更新当前结构，而不是追加流水账。
