# 开发进度

## 当前阶段状态

- 日期：**2026-04-07**
- 阶段：**单 Agent 最小链路验收收口**
- 状态：**Blocked**
- 阶段边界：只收口“单 Agent + 上下文治理阶段 A + CodeAct 阶段 A + 工具结果摘要化 / 卸载索引 / 按需回填”的最小链路，不进入上下文治理阶段 B 后续切片 / C、MCP 资源融合或 `Multi-Agent` 默认实现面。
- 当前判断：仓内主链路、分层边界与当前阶段测试集合已收口；当前未完成项只剩外部 OpenAI-compatible `live smoke` 验收。

## 当前阻塞

- `2026-04-07 17:04 CST` 复验 `./scripts/mvnw-local.sh -q -DskipTests compile` 通过，`2026-04-07 17:05 CST` 复验 `./scripts/mvnw-local.sh -q -DskipITs test` 通过。
- 当前外部 `live smoke` 实际命中配置为：
  `models=gpt-5.4, baseUrl=https://ruoli.dev/v1, apiKey=len=51,suffix=nLy0`
- 当前失败已收敛为确定性鉴权错误：
  `2026-04-07 17:05 CST` 运行 `./scripts/run-live-smoke.sh` 返回 `status=401`，错误摘要 `无效的令牌`，`request id=202604070905147409064708268d9d6eJhGVdBU`
- 因此当前阻塞不是仓内代码回归、分层越界、默认执行面扩张或 `-DskipITs test` 测试失败。

## 当前主线

- 主线只保留一条：恢复外部 OpenAI-compatible 验收。
- 任务 1：先核对 `base URL`、模型和 `api key` 是否属于同一可用渠道，先排除外部配置错配。
- 任务 2：确认外部配置后，只重跑 `./scripts/run-live-smoke.sh`，不穿插新的功能开发或额外重构。
- 任务 3：只有在外部配置已确认仍失败时，才进入 `aiframework transport/client/parser` 边界内的最小兼容排查。
- 在 `live smoke` 转绿前，不新增功能、不扩展默认执行面、不把兼容修正扩散到 `domain`、Controller 或配置层。

## 下一步入口

1. 核对 `https://ruoli.dev/v1` 是否提供 `gpt-5.4`，并确认当前 `api key` 与该渠道匹配且仍有效。
2. 仅在外部渠道、模型或凭证层修正配置后，重跑 `./scripts/run-live-smoke.sh`。
3. 若鉴权修正后仍失败，再依据新的状态码、错误摘要和 `request id`，只在 `aiframework transport/client/parser` 边界内做最小排查。
