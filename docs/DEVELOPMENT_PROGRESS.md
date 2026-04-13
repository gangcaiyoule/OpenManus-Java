# 开发进度

## 当前阶段状态

- 日期：**2026-04-14**
- 阶段：**单 Agent 最小链路验收收口**
- 状态：**In Progress**
- 当前阶段边界：只维护“单 Agent + 上下文治理阶段 A + CodeAct 阶段 A + 工具结果摘要化 / 卸载索引 / 按需回填 + MCP 工具发现/调用桥接”的最小责任面。
- 当前阶段结论：仓内主链路、分层边界和默认验证入口继续维持收口；`./scripts/run-live-smoke.sh` 在补入 `OPENMANUS_LIVE_CA_CERT_FILE` 后已进入 non-skipped 实调用，当前首个阻塞已前移为外部兼容网关 `402 insufficient_balance`。阶段未完成，当前不做阶段收口 commit。

## 当前阻塞

- 外部验收环境仍不可闭环。`2026-04-14` 复验中，默认环境下 `./scripts/run-live-smoke.sh` 会因 JVM 不信任兼容网关证书链而 `skipped`；在显式提供 `OPENMANUS_LIVE_CA_CERT_FILE` 后，live smoke 已转为真实调用并稳定失败于 `status=402`、`code=insufficient_balance`。
- 当前首个有效阻塞已经收敛为外部兼容网关余额/配额不可用，不再是脚本、TLS 接入或仓内主链路问题。

## 当前主线

1. 不扩阶段，只围绕单 Agent 当前验收闭环推进。
2. 继续维持仓内默认验证入口稳定；`2026-04-14` 复验中 `./scripts/mvnw-local.sh -q -DskipTests compile` 与 `./scripts/mvnw-local.sh -q -DskipITs test` 已通过。
3. 当前唯一主线是补齐 live smoke 外部成功证据；既然首个失败已收敛为 `402 insufficient_balance`，就不再对同一候选做盲重试。
4. 在验收闭环前，不进入 `Multi-Agent`、MCP 资源融合或上下文治理阶段 B/C 的新增实现。
5. 当前 MCP 责任面只保留工具发现与调用桥接，不把 `mcp.resource.read` 带回默认维护面。
6. 当前工作区仍有大量未收口改动；本轮协调只更新阶段判断与任务顺序，不做阶段收口提交。

## 下一步入口

1. 第一优先级：准备具备可信证书链且余额/配额可用的外部兼容网关环境，再重跑 `./scripts/run-live-smoke.sh`。
   入口标准：在 `OPENMANUS_LIVE_CA_CERT_FILE` 或等效可信证书链前提下，拿到 non-skipped 成功证据。
2. 第二优先级：若 live smoke 仍失败，只继续定位首个有效阻塞，不扩到阶段外问题。
   入口标准：明确首个阻塞是否仍为外部网关余额/配额、凭证或模型可用性，并同步更新结论。
3. 第三优先级：仅在 `compile`、`test`、`run-live-smoke.sh` 三个入口同时满足当前阶段口径后，再判断是否执行阶段收口 commit。
   入口标准：提交范围只包含当前阶段收口改动，不混入现有阶段外变更。
