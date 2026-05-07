# 开发计划

## 1. 开发目标

围绕 `aiframework` 构建稳定、可持续演进的 Agent 执行框架。文件级动态装载收尾已完成：上下文治理已从旧 artifact / rehydrate / snapshot 链路收敛为"工具原始结果 + API 前统一预算 + Shell 显式读取"。

## 2. 当前状态

- **日期**：2026-05-07
- **阶段**：文件级动态装载收尾已完成
- **状态**：compile + test 通过，阶段内验收闭环

已完成目标：

1. 大工具结果只由 `ToolResultBudget` 在 executor 调模型前统一处理。
2. 完整大结果只落到 `.openmanus/tool-results/`。
3. 模型请求中只看到 `[Tool Result Stub]`、相对 path、hash、长度与 head/tail preview。
4. stub 只提示模型通过 `runShellCommand` 使用 `cat/head/tail/grep/rg` 显式读取。
5. `SearchTool`、`WebFetchTool`、`ShellTool` 不自行落盘、不生成 preview stub、不做摘要压缩。
6. 后端 WebFetch 不再产出 `.openmanus/web` snapshot。
7. `FileReadTool`、`ContextBudgetPolicy`、`HistoricalContextSummarizer`、`ModelContextTokenCounter` 已清理删除。
8. 旧 `front/` 目录已迁移至 `frontend/`。

## 3. 工具策略

| 工具 | 策略 |
|---|---|
| `ToolResultBudget` | 唯一预算入口；超过阈值时写 `.openmanus/tool-results/{yyyyMMdd-HHmmssSSS}-{shortHash}.txt` 并生成 stub |
| `runShellCommand` | 唯一显式读取入口；当前期接受并执行所有 Shell 命令，只校验 `cwd` 在 workspace 内，命令内容由 Docker sandbox 隔离兜底 |
| `browser_fetch_web` | 返回网页原始正文：`url/contentType/originalChars/body`；不写 `.openmanus/web`，不返回 snapshot path/preview |
| `search_web` | 保留搜索结果条数上限；不做模型可见内容长度裁剪，大搜索结果交给 executor budget |
| `browser_open_url` | 在真实浏览器/VNC 中打开指定 URL |
| `browser_ensure_sandbox` | 确保 VNC 沙箱就绪并返回 VNC URL |
| `executePython` / `executePythonFile` | 在沙箱中执行 Python 代码/文件 |
| `recordTask` / `reflectOnTask` / `getTaskHistory` | 任务执行记录与反思分析 |

实现收敛：

- 会话沙箱大文本文件写入不再把内容内联到 shell 参数，统一通过 Docker archive copy 进入容器。
- Python 脚本执行不再使用 `python3 -c` 承载整段脚本，统一改为 `stdin` 流式传给 `python3 -`。

Shell 当前边界：

- 当前阶段不做 Shell 命令内容级 parser、读命令路径参数识别或复杂语法拒绝。
- `runShellCommand` 仍校验 `cwd`，确保工作目录解析在用户 workspace 内。
- Shell 命令实际在 Docker sandbox 中执行；当前以 Docker 隔离作为主要安全边界。

Search 与 WebFetch 的边界：

- `search_web` 用于发现候选网页、标题、链接和搜索摘要，不打开目标网页，不返回目标网页正文。
- `browser_fetch_web` 用于打开并抓取指定 URL 的原始响应正文，适合在已有 URL 后获取页面内容。
- 二者不是完全重复：Search 解决"找什么网页"，WebFetch 解决"读这个网页"。

## 4. 已清理旧链路

以下旧链路已完成清理，代码中不再存在：

- artifact store 抽象、实现、bean wiring 与相关配置。
- indexed rehydrate 与 `[Tool Result Rehydrated]`。
- 工具结果摘要压缩卡片。
- 旧历史摘要链路中的 artifact-specific 字段与说明。
- `FileReadTool` 注册、bean、builder 字段与测试闭环。
- `ContextBudgetPolicy`、`HistoricalContextSummarizer`、`ModelContextTokenCounter`。
- 旧 `front/` 目录（已迁移至 `frontend/`）。

## 5. 下一期计划

### 5.1 Shell 命令安全策略

评估 Shell 命令级校验，重点问题：

- 是否需要在 Docker sandbox 之外再做命令内容级 allowlist / denylist。
- 如何定义"读文件命令"：仅 `cat/head/tail/grep/rg`，还是包含 `sed/awk/find/perl/python/node/cp/tar` 等所有可读文件的入口。
- 如何处理 wrapper、管道、重定向、命令替换、`xargs`、`find -exec` 等复杂 shell 结构。
- 如果继续要求"全部 Shell 命令可执行"，是否只保留 `cwd` 校验，把越界读取完全交给 Docker/容器边界处理。
- 是否需要对 Docker sandbox 增加只读挂载、最小权限、网络/进程限制等硬化措施。

### 5.2 Web snapshot 前端迁移

后端已停止产出 `.openmanus/web` snapshot。前端仍保留旧 snapshot 字段用于兼容，后续迁移：

- 明确 WebFetch 原始正文如何展示。
- 决定是否需要前端 iframe snapshot、正文预览或仅保留 VNC/browser 视图。
- 清理 `snapshotPath/snapshotPreview` 兼容字段。

### 5.3 上下文治理阶段 C

- 将"计划、执行中间态、待办、失败原因"从普通聊天历史中结构化抽离。
- 支持在单次任务内恢复 agent 执行意图，而不依赖模型重复推断全部上下文。

### 5.4 MCP 资源融合

- 支持资源读取、上下文注入、会话级能力缓存。
- 让 agent 能区分"工具调用"和"资源装载"两类外部能力。

## 6. 验收命令

```bash
./scripts/mvnw-local.sh -q -Dtest=ToolResultBudgetTest,AgentToolResultBudgetE2ETest,ShellToolSmokeTest,WebFetchToolSmokeTest,SearchToolSmokeTest test
./scripts/mvnw-local.sh -q -DskipTests compile
./scripts/mvnw-local.sh -q -DskipITs test
```

全仓验收扫描：

```bash
rg -n "FileRead|fileReadTool|\\.openmanus/web|snapshotPath|snapshotPreview|artifactId|rehydrate|ToolResultContextCompressor|IndexedRehydrate|Tool Result Rehydrated" src/main/java src/test/java src/main/resources docs frontend
```

扫描预期：`src/main/java` 与 `src/test/java` 不应再出现模型工具注册、系统提示词或 E2E 调用中的 `FileRead`；`docs/` 可保留 review 依据中的历史问题描述；`frontend/` 中旧 snapshot UI 兼容字段不阻塞后端收敛。