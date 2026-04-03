# 开发进度

## 当前阶段状态

- 阶段：阶段 A 收口（live smoke 证据闭环）
- 状态：**Blocked（外部门禁未解除）**
- 阶段完成判定：`*LiveSmokeTest` 产出 non-skipped 结果，并回填 surefire 三项统计。
- 本轮验证：`./scripts/mvnw-local.sh -q -DskipITs test -Dtest='*LiveSmokeTest'` 已执行，结果为 `tests=3, failures=0, errors=0, skipped=3`。
- 当前判断：工作区已出现上下文治理、运行时抽象、MCP 相关在途改动，但在 live smoke 形成 non-skipped 证据前，这些改动不作为当前阶段推进依据。

## 当前阻塞

- 同一会话未注入 OpenAI / Anthropic / Gemini 三组 `OPENMANUS_LIVE_*` 变量。
- 当前首个阻塞分类：`Assumption failed`，三条 live smoke 均因缺少对应环境变量被跳过。
- 阶段控制风险：若继续并行推进后续阶段实现，`DEVELOPMENT_PROGRESS`、`Review` 与实际工作区会持续失配。

## 当前主线

1. 先补齐三组 `OPENMANUS_LIVE_*` 变量并在同一会话生效。
2. 仅执行 `./scripts/mvnw-local.sh -q -DskipITs test -Dtest='*LiveSmokeTest'` 验证真实 Provider 链路。
3. 回填当前轮 surefire `tests/failures/errors/skipped` 与首个失败分类。
4. 若产出 non-skipped 证据，再进入 `domain/service -> UnifiedWorkflow` 最小端口化收敛。
5. 在当前关口完成前，不以工作区中的 MCP、运行时或上下文增强草稿作为阶段切换依据。

阶段边界：
- 本阶段不进入上下文治理 B/C。
- 本阶段不进入 MCP 新能力开发。
- 本阶段不做跨层重构与提前抽象。

## 下一步入口

1. 入口动作：准备并注入三组 `OPENMANUS_LIVE_*` 变量。
2. 执行动作：运行 `./scripts/mvnw-local.sh -q -DskipITs test -Dtest='*LiveSmokeTest'`。
3. 结果动作：按 live smoke 结果更新本文件；若阶段状态变化，再同步收敛 `docs/Review.md`。
4. 提交动作：本轮允许单独提交进度文档；业务代码与跨阶段实现等待门禁解除后再按阶段拆分提交。
