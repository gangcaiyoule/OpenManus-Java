# 开发进度

## 当前阶段状态

- 日期：**2026-04-14**
- 阶段：**单 Agent 最小链路验收收口**
- 状态：**Blocked by External Live Smoke Environment**
- 当前边界：只维护“单 Agent + 上下文治理阶段 A + CodeAct 阶段 A + 工具结果摘要化 / 卸载索引 / 按需回填 + MCP 工具发现 / 调用桥接”的最小责任面，不进入 `Multi-Agent`、MCP 资源融合和上下文治理后续切片。
- 当前验收口径：`./scripts/mvnw-local.sh -q -DskipTests compile`、`./scripts/mvnw-local.sh -q -DskipITs test`、`./scripts/run-live-smoke.sh`。
- 当前结果：`compile`、`test` 已在本轮复验通过；`live smoke` 最新仍为 `tests=1, failures=0, errors=0, skipped=1`，首个问题为 `Assumption failed`。

## 当前阻塞

- 当前唯一阻塞仍是 `live smoke` 未拿到 non-skipped 成功结果。
- 最新首个真实问题仍为外部 TLS 信任链未就绪，根因保持为 `PKIX path building failed: SunCertPathBuilderException: unable to find valid certification path to requested target`。
- 该问题属于外部验收环境缺口，不回推成默认 `compile` / `test` 主链路改造任务。

## 当前主线

1. 保持当前阶段实现边界不再扩张，只做验收收口。
2. 外部环境修正只限 `live smoke` 进程参数与证书链，不改默认运行时代码路径。
3. 先为 `live smoke` 进程补齐外部 TLS 信任链，再复核 OpenAI-compatible 主链路。
4. 仅当拿到 non-skipped 成功结果后，才判断当前阶段是否可完成收口并进入下一阶段规划。

## 下一步入口

1. 为 `live smoke` 进程提供 `OPENMANUS_LIVE_CA_CERT_FILE`。
2. 重跑 `./scripts/run-live-smoke.sh`，确认 TLS 证书链问题是否解除。
3. 若仍无法握手，再按 `OPENMANUS_LIVE_JDK_TLS_CLIENT_PROTOCOLS`、`OPENMANUS_LIVE_HTTPS_PROTOCOLS`、`OPENMANUS_LIVE_JAVA_TOOL_OPTIONS` 的顺序做进程级最小修正。
4. `live smoke` 转为 non-skipped 成功后，更新阶段结论并确认是否进入下一阶段规划。
