# 开发计划

## 1. 开发目标

围绕 `aifrframework` 构建稳定、可持续演进的 Agent 执行框架。当前主线是文件级动态装载收尾：把上下文治理从旧 artifact / rehydrate / snapshot 链路收敛为“工具原始结果 + API 前统一预算 + Shell 显式读取”。

## 2. 当前状态

- **日期**：2026-05-04
- **阶段**：文件级动态装载收尾修复中
- **状态**：implementation-ready for cleanup

本轮修复目标：

1. 大工具结果只由 `ToolResultBudget` 在 executor 调模型前统一处理。
2. 完整大结果只落到 `.openmanus/tool-results/`。
3. 模型请求中只看到 `[Tool Result Stub]`、相对 path、hash、长度与 head/tail preview。
4. stub 只提示模型通过 `runShellCommand` 使用 `cat/head/tail/grep/rg` 显式读取。
5. `SearchTool`、`WebFetchTool`、`ShellTool` 不自行落盘、不生成 preview stub、不做摘要压缩。
6. 后端 WebFetch 不再产出 `.openmanus/web` snapshot。

## 3. 工具策略

| 工具 | 策略 |
|---|---|
| `ToolResultBudget` | 唯一预算入口；超过阈值时写 `.openmanus/tool-results/{yyyyMMdd-HHmmssSSS}-{shortHash}.txt` 并生成 stub |
| `runShellCommand` | 唯一显式读取入口；当前期接受并执行所有 Shell 命令，只校验 `cwd` 在 workspace 内，命令内容由 Docker sandbox 隔离兜底 |
| `browser_fetch_web` | 返回网页原始正文：`url/contentType/originalChars/body`；不写 `.openmanus/web`，不返回 snapshot path/preview |
| `search_web` | 保留搜索结果条数上限；不做模型可见内容长度裁剪，大搜索结果交给 executor budget |

Shell 当前边界：

- 当前阶段不做 Shell 命令内容级 parser、读命令路径参数识别或复杂语法拒绝。
- `runShellCommand` 仍校验 `cwd`，确保工作目录解析在用户 workspace 内。
- Shell 命令实际在 Docker sandbox 中执行；本轮以 Docker 隔离作为主要安全边界。
- `cat/head/tail/grep/rg` 等命令仍是模型读取 `.openmanus/tool-results/` 的推荐方式，但不在工具层单独拦截其他 Shell 语法。

Search 与 WebFetch 的边界：

- `search_web` 用于发现候选网页、标题、链接和搜索摘要，不打开目标网页，不返回目标网页正文。
- `browser_fetch_web` 用于打开并抓取指定 URL 的原始响应正文，适合在已有 URL 后获取页面内容。
- 二者不是完全重复：Search 解决“找什么网页”，WebFetch 解决“读这个网页”。
- 未来可以考虑将 WebFetch 返回内容进一步做正文抽取、charset 识别或 MIME 策略；本轮只做后端停止 snapshot 产出。

## 4. 已清理旧链路

以下方向不再作为默认上下文治理能力：

- artifact store 抽象、实现、bean wiring 与相关配置。
- indexed rehydrate 与 `[Tool Result Rehydrated]`。
- 工具结果摘要压缩卡片。
- `HistoricalContextSummarizer` 中 artifact-specific 字段。
- 默认模型可见 `FileRead` 工具面。

## 5. 本轮不做

- 前端 Web snapshot 体验迁移。
- 显式多用户隔离 E2E。
- 未提交状态治理。
- 前端 Web 预览体验重构。
- Shell 命令内容级安全 parser、读命令路径识别和 wrapper/管道/重定向策略。

## 6. 下一期候选计划

### 6.1 Shell 命令安全策略

下一期再统一评估 Shell 命令级校验，重点问题：

- 是否需要在 Docker sandbox 之外再做命令内容级 allowlist / denylist。
- 如何定义“读文件命令”：仅 `cat/head/tail/grep/rg`，还是包含 `sed/awk/find/perl/python/node/cp/tar` 等所有可读文件的入口。
- 如何处理 wrapper、管道、重定向、命令替换、`xargs`、`find -exec` 等复杂 shell 结构。
- 如果继续要求“全部 Shell 命令可执行”，是否只保留 `cwd` 校验，把越界读取完全交给 Docker/容器边界处理。
- 是否需要对 Docker sandbox 增加只读挂载、最小权限、网络/进程限制等硬化措施。

### 6.2 Web snapshot 前端迁移

后端已停止产出 `.openmanus/web` snapshot。前端仍保留旧 snapshot 字段用于兼容，后续单独迁移：

- 明确 WebFetch 原始正文如何展示。
- 决定是否需要前端 iframe snapshot、正文预览或仅保留 VNC/browser 视图。
- 清理 `snapshotPath/snapshotPreview` 兼容字段。

## 7. 验收计划

先跑定向测试：

```bash
./scripts/mvnw-local.sh -q -Dtest=ToolResultBudgetTest,AgentToolResultBudgetE2ETest,ShellToolSmokeTest,WebFetchToolSmokeTest,SearchToolSmokeTest test
```

再跑编译：

```bash
./scripts/mvnw-local.sh -q -DskipTests compile
```

最后跑默认回归：

```bash
./scripts/mvnw-local.sh -q -DskipITs test
```

全仓验收扫描：

```bash
rg -n "FileRead|fileReadTool|\\.openmanus/web|snapshotPath|snapshotPreview|artifactId|rehydrate|ToolResultContextCompressor|IndexedRehydrate|Tool Result Rehydrated" src/main/java src/test/java src/main/resources docs front
```

扫描预期：`src/main/java` 与 `src/test/java` 不应再出现模型工具注册、系统提示词或 E2E 调用中的 `FileRead`；`docs/` 可保留 review 依据中的历史问题描述；`front/` 中旧 snapshot UI 兼容字段不阻塞本轮后端收敛。
