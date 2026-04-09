# 开发进度

## 当前阶段状态

- 日期：**2026-04-09**
- 阶段：**单 Agent 最小链路验收收口**
- 状态：**In Progress**
- 当前阶段边界：只维护“单 Agent + 上下文治理阶段 A + CodeAct 阶段 A + 工具结果摘要化 / 卸载索引 / 按需回填 + MCP 工具发现/调用桥接”的最小责任面。
- 当前阶段结论：代码主链路、分层边界和默认测试已经重新收口；`mcp.resource.read` 已退出默认配置、装配与阶段验收面，但阶段验收还没有完全闭环。

## 当前阻塞

- `./scripts/run-live-smoke.sh` 的 non-skipped 成功证据仍未产出，当前阶段还不能按“已完成”收口。
- 本地默认验证入口对 JDK 版本不自洽；若 `JAVA_HOME` 指向 Java 17，`./scripts/mvnw-local.sh` 仍会被 Enforcer 拦截。
- `aiframework` 内部仍保留 `mcp.resource.read` 预埋实现和测试，虽然不在默认装配面，但仍扩大了当前阶段维护面。

## 当前主线

1. 不扩阶段，只围绕单 Agent 阶段验收闭环推进。
2. 先解决“验证入口是否可直接复现”的问题，再补 live smoke 外部验收证据。
3. 在阶段验收没有闭环前，不进入 `Multi-Agent`、MCP 资源融合或上下文治理阶段 B/C 的新实现。
4. 若 MCP resource-read 不会立刻进入下一阶段，就继续从 `aiframework` 内部收缩对应预埋复杂度；若暂时保留，只按阶段外预研能力处理。

## 下一步入口

1. 第一优先级：收口 Maven 验证入口。
   入口标准：`./scripts/mvnw-local.sh -q -DskipTests compile` 与 `./scripts/mvnw-local.sh -q -DskipITs test` 在默认本地环境可直接复现，或文档统一改成显式要求 Java 21。
2. 第二优先级：在环境满足时补跑 `./scripts/run-live-smoke.sh`。
   入口标准：拿到 non-skipped 成功证据；若环境仍不满足，则继续维持阶段 `In Progress`，不提前宣告完成。
3. 第三优先级：决定 `mcp.resource.read` 的当前阶段处理方式。
   入口标准：要么继续删除 `aiframework` 内部预埋分支和对应测试，要么在文档中明确其不属于当前阶段默认维护基线。
