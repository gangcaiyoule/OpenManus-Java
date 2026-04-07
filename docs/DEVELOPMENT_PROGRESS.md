# 开发进度

## 当前阶段状态

- 日期：**2026-04-07**
- 阶段：**单 Agent 最小链路验收收口**
- 状态：**Blocked**
- 阶段边界：当前实现仍收敛在“单 Agent + 上下文治理阶段 A + CodeAct 阶段 A + 工具结果摘要化 / 卸载索引 / 按需回填”的最小链路内，不进入上下文治理阶段 B 后续切片 / C、MCP 资源融合或 `Multi-Agent` 默认实现面。
- 当前基线：`2026-04-07 16:28 CST` 的定向 Java 守卫与主链路测试、前端 `test` 已复验通过；同次执行 `./scripts/run-live-smoke.sh` 时，脚本先报 `line 56: parts[@]: unbound variable`，随后 `OpenAiClientLiveSmokeTest` 仍以 `status=401`、`message="无效的令牌"` 失败，最新 request id 为 `202604070828397533628688268d9d6Nu5Rn4Aq`。
- 当前结论：仓内主链路保持收敛，当前只处理两个已确认阻塞：先修 `run-live-smoke.sh` 的空候选列表回归，再处理外部 OpenAI-compatible 渠道鉴权。
- 当前提交判断：保留一次文档协调性 checkpoint commit，用于同步当前阻塞和下一步入口；不做“阶段完成”类 commit。

## 当前阻塞

- `./scripts/run-live-smoke.sh` 在配置摘要阶段仍存在脚本回归：`append_unique_csv_values` 在空候选列表下访问未初始化数组，导致 `line 56: parts[@]: unbound variable`。
- 现有 `LiveSmokeScriptIntegrationTest` 尚未覆盖“只有 `OPENMANUS_LIVE_MODEL`、无 `OPENMANUS_LIVE_MODEL_CANDIDATES`”时的配置摘要分支。
- OpenAI-compatible `live smoke` 的外部鉴权仍失败；本轮同次复验仍返回 `401` `无效的令牌`。
- 在该问题解决前，不扩展新的实现面。

## 当前主线

- 主线顺序固定为：修正 `run-live-smoke.sh` 空候选列表回归 -> 补齐脚本测试覆盖 -> 核对 `live smoke` 实际命中配置与外部鉴权 -> 重跑 `./scripts/run-live-smoke.sh` -> `live smoke` 转绿后再统一确认四项验收。
- 当前阶段只允许围绕 `live smoke` 阻塞推进，不新增功能、不切入下一阶段、不把兼容修正扩散到 `domain`、Controller 或配置层。
- 当前开发顺序按三步收敛：先修脚本诊断入口并补测，再处理渠道鉴权，最后才判断是否进入阶段验收完成。
- 当前仓内验证结论：`./scripts/mvnw-local.sh -q -DskipITs -Dtest=LiveSmokeScriptIntegrationTest,LiveSmokeEnvTest,OpenAiClientLiveSmokeTestTest test` 已通过，但脚本测试覆盖仍需补齐上述空候选列表分支。

## 下一步入口

1. 先修复 `run-live-smoke.sh` 对空 `OPENMANUS_LIVE_MODEL_CANDIDATES` 的处理。
2. 补一条脚本集成测试，覆盖“只有主模型、无候选列表”时的配置摘要输出。
3. 然后只处理 `live smoke` 实际命中配置与外部鉴权问题，优先核对 `api key` 是否对应该 `base URL`，以及当前令牌是否仍有效、是否具备 `gpt-5.4` 的可用权限。
4. 修正后仅重跑 `./scripts/run-live-smoke.sh`；转绿后再统一确认四项阶段验收是否全部满足。
