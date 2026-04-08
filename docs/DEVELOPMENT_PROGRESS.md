# 开发进度

## 当前阶段状态

- 日期：**2026-04-09**
- 阶段：**单 Agent 最小链路验收收口**
- 状态：**Blocked**
- 阶段边界：只收口“单 Agent + 上下文治理阶段 A + CodeAct 阶段 A + 工具结果摘要化 / 卸载索引 / 按需回填”的最小链路，不进入上下文治理阶段 B 后续切片 / C、MCP 资源融合或 `Multi-Agent` 默认实现面。
- 阶段结论：仓内 `compile` 与默认 `test` 已通过，当前未发现新的仓内实现缺口；阶段唯一阻塞仍是 live smoke 外部 TLS 证书链。
- 提交判断：当前只允许文档级协调提交，不做阶段完成提交，也不混入新的实现改动。

## 当前阻塞

- 仓内验证已收口：`./scripts/mvnw-local.sh -q -DskipTests compile` 与 `./scripts/mvnw-local.sh -q -DskipITs test` 已通过。
- 外部验收未收口：`./scripts/run-live-smoke.sh` 失败，当前仍从仓库根 `.env` 回填默认 live smoke 配置。
- 当前失败稳定收敛为 `https://api.weclawai.cc/v1` 的 TLS 证书链问题：`PKIX path building failed` / `unable to find valid certification path to requested target`。

## 当前主线

1. 不再扩大仓内实现范围，也不再插入与当前阻塞无关的结构调整。
2. 第一顺位只处理 live smoke 外部验收闭环，优先补当前网关可用的 PEM 证书链。
3. 若证书链不可得或复验仍失败，再切换到证书链完整且与当前模型、凭证同源可用的 OpenAI-compatible 网关。
4. 只有在 live smoke 转绿后，才回到阶段完成判断与提交策略收口。

## 下一步入口

- 优先准备当前网关可用的 PEM 证书链，并通过 `OPENMANUS_LIVE_CA_CERT_FILE=<pem> ./scripts/run-live-smoke.sh` 复验。
- 若无法提供匹配证书链，则切换到证书链完整且与当前 `apiKey`、模型同源可用的 OpenAI-compatible 网关后再复验。
- 若继续沿用当前 `.env` 的默认 LLM 配置，需同步确认 `https://api.weclawai.cc/v1` 的证书链、模型可用性与凭证归属仍然一致，避免在证书问题解决后暴露新的 `401` 或 `model_not_found` 环境错误。
- 仅在 live smoke 转绿后，再回到阶段完成判断与提交策略收口。
