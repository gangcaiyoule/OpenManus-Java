# 开发进度

## 当前阶段状态

- 日期：**2026-04-14**
- 阶段：**单 Agent 最小链路验收收口**
- 状态：**In Progress**
- 当前阶段边界：只维护“单 Agent + 上下文治理阶段 A + CodeAct 阶段 A + 工具结果摘要化 / 卸载索引 / 按需回填 + MCP 工具发现/调用桥接”的最小责任面。
- 当前阶段结论：仓内主链路、分层边界和默认验证入口继续维持收口。`./scripts/mvnw-local.sh -q -DskipTests compile`、`./scripts/mvnw-local.sh -q -DskipITs test` 已于 `2026-04-14` 实际复验通过；`./scripts/run-live-smoke.sh` 同日最新实测结果为 `tests=1, failures=0, errors=0, skipped=1`，首个有效阻塞仍是外部兼容网关证书链校验失败 `PKIX path building failed`。阶段未完成。

## 当前阻塞

- 外部验收环境仍不可闭环。当前首个有效阻塞是兼容网关证书链校验失败 `PKIX path building failed`；在该问题消除前，不应继续假设后续一定会前移到握手、鉴权或余额/配额问题。

## 当前主线

1. 不扩阶段，只围绕“单 Agent 当前验收闭环”推进，不新增 `Multi-Agent`、MCP 资源融合或上下文治理阶段 B/C 的实现面。
2. 仓内主链路本轮不再继续扩写；当前默认验证入口以 `2026-04-14` 已通过的 `compile`、`test` 结果为基线，只做收口维护。
3. 外部验收只处理 live smoke 当前首个真实阻塞；排障顺序固定为“证书链/TLS -> non-skipped 实调用 -> 再确认新的首个阻塞”。
4. live smoke 可用调优入口仅限 `OPENMANUS_LIVE_CA_CERT_FILE`、`OPENMANUS_LIVE_JDK_TLS_CLIENT_PROTOCOLS`、`OPENMANUS_LIVE_HTTPS_PROTOCOLS`、`OPENMANUS_LIVE_JAVA_TOOL_OPTIONS`，且这些入口只作用于 live smoke 进程，不回流应用默认运行时。
5. 仅在 `compile`、`test`、`run-live-smoke.sh` 三个入口同时满足当前阶段口径后，再判断是否进入阶段收口。

## 下一步入口

1. 第一入口：为兼容网关补齐有效 CA 证书链，或在 live smoke 入口显式收敛 TLS/JVM 参数后重跑 `./scripts/run-live-smoke.sh`。
   入口标准：live smoke 先脱离 `PKIX path building failed`，进入 non-skipped 实调用。
2. 第二入口：若仍失败，只记录最新一次实测暴露的首个有效外部阻塞，并同步收敛文档结论。
   入口标准：不沿用旧阻塞描述，不并行展开第二个外部问题。
3. 第三入口：仅在三类验证入口全部满足当前阶段口径后，再判断是否进入阶段收口与提交节点。
   入口标准：阶段结论只保留当前有效状态、当前主线和最小下一步动作。
