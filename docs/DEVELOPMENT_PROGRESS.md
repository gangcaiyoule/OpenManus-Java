# 开发进度

## 当前阶段状态

- 日期：**2026-04-14**
- 阶段：**单 Agent 最小链路验收收口**
- 状态：**In Progress**
- 当前阶段边界：只维护“单 Agent + 上下文治理阶段 A + CodeAct 阶段 A + 工具结果摘要化 / 卸载索引 / 按需回填 + MCP 工具发现/调用桥接”的最小责任面。
- 当前阶段结论：截至 `2026-04-14` 的文档与代码复核，阶段主线仍需先收口 Web 边界，再回到外部 live smoke 验收；当前三个最小缺口仍是代理错误脱敏、监控控制器参数校验和 WebSocket 心跳负载兜底。

## 当前阻塞

- Web 边界未收口。`WebProxyController` 仍把上游异常细节拼进 `502` 响应；`AgentMonitoringController` 仍缺少 `limit` / `maxAgeHours` 非法参数的 `400` 收口；`WebSocketHeartbeatController` 仍缺少空负载与非数字 `timestamp` 兜底。
- 外部验收仍受环境阻塞。最近一次有效 live smoke 结论仍是兼容网关 TLS 证书链不被当前 JVM 信任，non-skipped 成功证据尚未建立。

## 当前主线

1. 阶段边界保持不变，不新增 `Multi-Agent`、MCP 资源融合或上下文治理阶段 B/C 的实现面。
2. 当前开发顺序固定为：先处理 Web 入口错误/参数边界，再做 `compile` / 默认 `test` 回归，最后回到 live smoke。
3. Web 边界阶段只做最小修正和测试补齐，不借机扩展控制器职责、不回推到 `domain` 层，也不引入新的运行时开关。
4. live smoke 仍只处理外部验收入口：优先补兼容网关 CA 证书链，其次才考虑 live smoke 专属 TLS/JVM 参数；不因外部环境阻塞回改应用默认主链路。

## 下一步入口

1. 先修复 `WebProxyController` 的 `502` 响应脱敏，并同步更新 `WebProxyControllerTest`，去掉对上游异常明文透传的断言依赖。
2. 为 `AgentMonitoringController` 补齐 `limit <= 0`、`maxAgeHours <= 0` 的 `400` 参数校验和对应测试，避免非法输入被误收口成 `500`。
3. 为 `WebSocketHeartbeatController` 增加空负载与非数字 `timestamp` 的兜底解析，并补一组控制器级回归测试锁住行为。
4. Web 边界问题收口后，重跑 `./scripts/mvnw-local.sh -q -DskipTests compile` 与 `./scripts/mvnw-local.sh -q -DskipITs test`，确认默认验证入口稳定。
5. 默认回归通过后，再通过 `OPENMANUS_LIVE_CA_CERT_FILE` 为 live smoke 注入兼容网关 CA；若仍被 TLS 阻塞，再最小范围补 `OPENMANUS_LIVE_JDK_TLS_CLIENT_PROTOCOLS`、`OPENMANUS_LIVE_HTTPS_PROTOCOLS` 或 `OPENMANUS_LIVE_JAVA_TOOL_OPTIONS`，随后重跑 `./scripts/run-live-smoke.sh` 并只记录新的首个真实阻塞。
