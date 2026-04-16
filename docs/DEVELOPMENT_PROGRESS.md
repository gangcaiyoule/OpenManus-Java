# 开发进度

## 当前阶段状态

- 日期：**2026-04-16**
- 阶段：**单 Agent 最小链路验收收口**
- 状态：**Blocked by External Live Smoke Environment**
- 当前边界：只收口“单 Agent + 上下文治理阶段 A + CodeAct 阶段 A + 工具结果摘要化 / 卸载索引 / 按需回填 + MCP 工具发现 / 调用桥接”的最小责任面；不进入 `Multi-Agent`、MCP 资源融合和上下文治理后续切片。
- 当前验收口径：`./scripts/mvnw-local.sh -q -DskipTests compile`、`./scripts/mvnw-local.sh -q -DskipITs test`、`./scripts/run-live-smoke.sh`。
- 当前验收结果：`2026-04-16` 已复验 `compile`、`test` 通过；`live smoke` 仍为 `tests=1, failures=0, errors=0, skipped=1`，首个真实问题为 `PKIX path building failed: SunCertPathBuilderException: unable to find valid certification path to requested target`。

## 当前阻塞

- 当前唯一阻塞仍是 `live smoke` 未拿到 non-skipped 成功结果。
- 首个真实问题已复验为外部网关证书链不被 JVM 信任，详情为 `PKIX path building failed: SunCertPathBuilderException: unable to find valid certification path to requested target`。
- 该问题属于外部验收环境缺口，不回推成默认 `compile` / `test` 主链路改造任务。

## 当前主线

1. 继续保持当前阶段实现边界不扩张，只做验收收口。
2. 仓库内主链路以 `compile`、`test` 可持续回归为准，不新增阶段内实现任务。
3. 外部环境修正只限 `live smoke` 进程的证书链和 TLS 参数，不改默认运行时代码路径。
4. 仅当 `live smoke` 拿到 non-skipped 成功证据后，才判断当前阶段是否完成并进入下一阶段规划。

## 下一步入口

1. 第一步：为 `live smoke` 进程提供 `OPENMANUS_LIVE_CA_CERT_FILE`，先补齐证书链信任。
2. 第二步：重跑 `./scripts/run-live-smoke.sh`，只看首个真实失败是否前移。
3. 第三步：若仍无法握手，再按 `OPENMANUS_LIVE_JDK_TLS_CLIENT_PROTOCOLS`、`OPENMANUS_LIVE_HTTPS_PROTOCOLS`、`OPENMANUS_LIVE_JAVA_TOOL_OPTIONS` 的顺序做进程级最小修正。
4. 第四步：仅当 `live smoke` 转为 non-skipped 成功后，再更新阶段结论。
