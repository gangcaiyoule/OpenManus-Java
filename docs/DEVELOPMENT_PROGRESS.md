# 开发进度

## 当前阶段状态

- 日期：**2026-04-14**
- 阶段：**单 Agent 最小链路验收收口**
- 状态：**In Progress**
- 当前阶段边界：只维护“单 Agent + 上下文治理阶段 A + CodeAct 阶段 A + 工具结果摘要化 / 卸载索引 / 按需回填 + MCP 工具发现/调用桥接”的最小责任面。
- 当前阶段结论：仓内主链路、分层边界和默认验证入口继续维持收口；`./scripts/mvnw-local.sh -q -DskipTests compile`、`./scripts/mvnw-local.sh -q -DskipITs test` 已通过。`./scripts/run-live-smoke.sh` 在默认环境下仍会因兼容网关 TLS 证书链未被 JVM 信任而 `skipped`；在显式提供 `OPENMANUS_LIVE_CA_CERT_FILE` 后，首个真实外部阻塞已前移为兼容网关 `402 insufficient_balance`。阶段未完成。

## 当前阻塞

- 外部验收环境仍不可闭环。`2026-04-14` 复验中，默认环境下 `./scripts/run-live-smoke.sh` 的首个跳过原因为 `PKIX path building failed`；在显式提供 `OPENMANUS_LIVE_CA_CERT_FILE` 后，live smoke 已进入 non-skipped 实调用，当前首个有效外部阻塞为 `status=402`、`code=insufficient_balance`。

## 当前主线

1. 不扩阶段，只围绕单 Agent 当前验收闭环推进。
2. 继续维持仓内默认验证入口稳定；`2026-04-14` 复验中 `./scripts/mvnw-local.sh -q -DskipTests compile`、`./scripts/mvnw-local.sh -q -DskipITs test` 已通过。
3. 当前第一开发顺序是外部验收环境收口，不再继续修改仓内主链路实现。
4. 当前第二开发顺序是准备“可信证书链 + 可用余额/配额”的外部兼容网关环境，再重跑 `./scripts/run-live-smoke.sh`，只记录首个有效阻塞，不做阶段外排障扩张。
5. 仅在 `compile`、`test`、`run-live-smoke.sh` 三个入口同时满足当前阶段口径后，再判断是否进入阶段收口。
6. 在验收闭环前，不进入 `Multi-Agent`、MCP 资源融合或上下文治理阶段 B/C 的新增实现；当前 MCP 责任面仍只保留工具发现与调用桥接。

## 下一步入口

1. 第一优先级：准备具备可信证书链且余额/配额可用的外部兼容网关环境，再重跑 `./scripts/run-live-smoke.sh`。
   入口标准：在 `OPENMANUS_LIVE_CA_CERT_FILE` 或等效可信证书链前提下，拿到 non-skipped 成功证据。
2. 第二优先级：若 live smoke 仍失败，只继续定位首个有效阻塞，不扩到阶段外问题。
   入口标准：优先确认是否仍为凭证、配额或模型可用性类错误；若是，只同步更新首个外部阻塞结论。
3. 第三优先级：仅在 `compile`、`test`、`run-live-smoke.sh` 三个入口同时满足当前阶段口径后，再判断是否进入阶段收口。
   入口标准：阶段结论只保留当前有效状态与最小后续动作，不回退到过程流水账。
