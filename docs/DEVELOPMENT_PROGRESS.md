# 开发进度

## 当前阶段状态

- 日期：**2026-04-14**
- 阶段：**单 Agent 最小链路验收收口**
- 状态：**In Progress**
- 当前阶段边界：只维护“单 Agent + 上下文治理阶段 A + CodeAct 阶段 A + 工具结果摘要化 / 卸载索引 / 按需回填 + MCP 工具发现/调用桥接”的最小责任面。
- 当前阶段结论：仓内主链路、分层边界和默认验证入口继续维持收口。`./scripts/mvnw-local.sh -q -DskipTests compile`、`./scripts/mvnw-local.sh -q -DskipITs test` 已在本轮复验通过。当前未闭环项只剩外部 live smoke 验收：默认环境下首个阻塞仍是兼容网关 TLS 证书链未被 JVM 信任导致的 `PKIX path building failed`；在补齐可信证书链后，已知下一个外部阻塞会前移到网关余额/配额。阶段未完成。

## 当前阻塞

- 外部验收环境仍不可闭环。当前必须先补齐兼容网关可信证书链，消除 `PKIX path building failed`；证书链打通后，外部网关仍需具备可用余额/配额，否则首个有效阻塞将前移为 `402 insufficient_balance`。

## 当前主线

1. 不扩阶段，只围绕单 Agent 当前验收闭环推进。
2. 继续维持仓内默认验证入口稳定；`2026-04-14` 复验中 `./scripts/mvnw-local.sh -q -DskipTests compile`、`./scripts/mvnw-local.sh -q -DskipITs test` 已通过。
3. 当前剩余工作只收敛在外部验收环境，不再继续扩写仓内主链路实现。
4. 验收推进顺序固定为“可信证书链 -> non-skipped 实调用 -> 首个真实外部阻塞”，每次只处理一个入口问题，不做阶段外排障扩张。
5. 仅在 `compile`、`test`、`run-live-smoke.sh` 三个入口同时满足当前阶段口径后，再判断是否进入阶段收口。
6. 在验收闭环前，不进入 `Multi-Agent`、MCP 资源融合或上下文治理阶段 B/C 的新增实现；当前 MCP 责任面仍只保留工具发现与调用桥接。

## 下一步入口

1. 第一优先级：准备兼容网关可信证书链，再重跑 `./scripts/run-live-smoke.sh`。
   入口标准：先消除 `PKIX path building failed`，使 live smoke 进入 non-skipped 实调用。
2. 第二优先级：若 live smoke 仍失败，只继续定位首个有效阻塞，不扩到阶段外问题。
   入口标准：若证书链已打通，则只记录新的首个外部阻塞；当前已知下一类阻塞是余额/配额不足导致的 `402 insufficient_balance`，仍归为外部环境问题，不展开额外能力开发。
3. 第三优先级：仅在 `compile`、`test`、`run-live-smoke.sh` 三个入口同时满足当前阶段口径后，再判断是否进入阶段收口。
   入口标准：阶段结论只保留当前有效状态与最小后续动作，不回退到过程流水账。
