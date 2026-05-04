# 文件级动态装载收尾 Review

## 当前状态

本轮收尾目标是把大工具结果治理收敛到唯一入口：

- `SearchTool`、`WebFetchTool`、`ShellTool` 只返回原始工具结果。
- `ToolResultBudget` 在 executor 调模型前统一决定是否落盘到 `.openmanus/tool-results/` 并替换为 stub。
- 模型只通过 `runShellCommand` 使用 `cat/head/tail/grep/rg` 等命令显式读取落盘文件。
- 后端不再产出 `.openmanus/web` snapshot；前端旧 snapshot UI 兼容字段迁移另开计划。
- 当前期 `ShellTool` 不做命令内容级拒绝；只校验 `cwd` 在 workspace 内，命令执行安全主要由 Docker sandbox 兜底。

## Review Findings

| Finding | 风险 | 修复目标 | 验收状态 |
|---|---|---|---|
| 1. `FileRead` 仍模型可见 | 默认工具链、系统提示词或 MCP local registry 继续暴露旧读取面 | 删除 `FileReadTool` 注册、bean、builder 字段、测试闭环与系统提示词描述 | 本轮修复 |
| 2. `WebFetchTool` 绕过预算写 `.openmanus/web` | 工具层生成第二套大结果落盘与 preview 语义 | WebFetch 返回 `url/contentType/originalChars/body`，不写 snapshot，不返回 `path/preview` | 本轮修复 |
| 3. `ShellTool` 读文件命令未做路径校验 | 模型可用 Shell 读取容器内任意路径 | 当前期取消命令级拒绝，仅保留 `cwd` 校验并依赖 Docker sandbox；读命令路径解析移入下一期安全策略设计 | 下一期规划 |
| 4. `ToolResultBudget` stub 仍提示 `FileRead` | stub 引导模型调用已移除工具 | `readHint` 只提示 `runShellCommand` + `cat/head/tail/grep/rg` | 本轮修复 |
| 5. Agent E2E 仍验证旧 `FileRead` 闭环 | 测试证明的是旧路径，不证明 Shell-only 读取可用 | E2E 改为大工具结果 -> stub -> `.openmanus/tool-results/` -> Shell 安全读取 | 本轮修复 |
| 6. `HistoricalContextSummarizer` 仍有 `artifactId` 残留 | 历史摘要继续传播旧 artifact 语义 | 删除 artifact-specific 提取与输出，只保留普通 `tool=`、`detail=` 摘要 | 本轮修复 |

## Additional Findings

- `SearchTool` 仍有工具层 8000 字符裁剪；本轮移除模型可见返回内容和 result item snippet 的裁剪，让大输出统一进入 `ToolResultBudget`。
- 前端仍可能消费 WebFetch snapshot 字段；本轮只做后端收敛，前端 snapshot 体验迁移另开计划。
- Shell 命令级安全 parser 容易误伤“全部 Shell 命令可执行”的目标，也无法完整覆盖所有可读文件入口；本轮移除命令内容拒绝逻辑，下一期结合 Docker sandbox 硬化统一设计。

## Search 与 WebFetch 边界

- `search_web`：用于搜索和发现候选 URL，返回标题、链接和摘要。
- `browser_fetch_web`：用于打开并抓取指定 URL 的响应正文，返回 `url/contentType/originalChars/body`。
- 两者不是完全重复；典型链路是先 Search 找候选，再 WebFetch 读取目标页面。

## 验收命令

```bash
./scripts/mvnw-local.sh -q -Dtest=ToolResultBudgetTest,AgentToolResultBudgetE2ETest,ShellToolSmokeTest,WebFetchToolSmokeTest,SearchToolSmokeTest test
./scripts/mvnw-local.sh -q -DskipTests compile
./scripts/mvnw-local.sh -q -DskipITs test
rg -n "FileRead|fileReadTool|\\.openmanus/web|snapshotPath|snapshotPreview|artifactId|rehydrate|ToolResultContextCompressor|IndexedRehydrate|Tool Result Rehydrated" src/main/java src/test/java src/main/resources docs front
```

扫描说明：`docs/` 中允许出现 review 记录里的历史问题描述；`front/` 中若仍有 snapshot UI 兼容字段，不阻塞本轮后端收敛。
