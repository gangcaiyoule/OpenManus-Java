# 开发进度

## 当前阶段状态

- 日期：**2026-04-14**
- 阶段：**单 Agent 最小链路验收收口**
- 状态：**In Progress**
- 当前阶段边界：只维护“单 Agent + 上下文治理阶段 A + CodeAct 阶段 A + 工具结果摘要化 / 卸载索引 / 按需回填 + MCP 工具发现/调用桥接”的最小责任面。
- 当前阶段结论：`./scripts/mvnw-local.sh -q -DskipTests compile` 与 `./scripts/mvnw-local.sh -q -DskipITs test` 通过；最新 `./scripts/run-live-smoke.sh` 仍为 `tests=1, failures=0, errors=0, skipped=1`，首个真实阻塞仍是外部兼容网关证书链校验失败 `PKIX path building failed`。当前不进入新增实现，只允许围绕 live smoke 证书链/TLS 入口继续收口。

## 当前阻塞

- 外部验收环境仍不可闭环。当前首个有效阻塞是兼容网关 TLS 证书链不被当前 JVM 信任，因此 live smoke 仍停留在 assumption skip，未进入 non-skipped 成功验收。

## 当前主线

1. 阶段边界保持不变，不新增 `Multi-Agent`、MCP 资源融合或上下文治理阶段 B/C 的实现面。
2. 仓内主链路继续以当前 `compile` / `test` 通过结果作为基线，不因外部 live smoke 阻塞回改应用默认运行时。
3. 当前执行顺序固定为：先补兼容网关 CA 证书链，再补 live smoke 专属 TLS/JVM 入口参数，随后重跑 `./scripts/run-live-smoke.sh`，只处理新出现的首个真实阻塞。

## 下一步入口

1. 优先为当前兼容网关准备可被 JVM 信任的 CA 证书链，或在 live smoke 入口显式提供 `OPENMANUS_LIVE_CA_CERT_FILE`。
2. 若仅补 CA 仍无法脱离阻塞，再按最小范围补充 `OPENMANUS_LIVE_JDK_TLS_CLIENT_PROTOCOLS`、`OPENMANUS_LIVE_HTTPS_PROTOCOLS` 或 `OPENMANUS_LIVE_JAVA_TOOL_OPTIONS`，且只作用于 `run-live-smoke.sh`。
3. 重跑 `./scripts/run-live-smoke.sh` 后立即更新阶段判断：若拿到 non-skipped 成功结果，则准备阶段完成收口；若仍失败，只记录新的首个真实阻塞并继续小步推进。
