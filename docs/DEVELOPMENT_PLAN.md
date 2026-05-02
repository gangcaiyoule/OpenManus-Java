# 开发计划

## 1. 开发目标

围绕 `aifrframework` 构建一个更稳定、可持续演进的 Agent 执行框架。

### 1.1 总目标

1. 上下文管理能力建设
2. `CodeAct + MCP` 执行能力建设
3. `Multi-Agent` 协同能力研究

### 1.2 目标一：上下文管理

成熟动态上下文系统包含四块能力：

| 能力 | 目标 | 状态 |
|------|------|------|
| 结构化事实记忆 | 可检索的 facts/decisions/constraints | ❌ 未实现 |
| 文件级动态装载 | 工作区文件按需发现、按需读取、按预算注入 | ⏳ **下一步** |
| MCP resource 融合 | agent 知道有哪些资源可按需读取 | ❌ 未实现 |
| 更强任务分治 | 主任务与子任务上下文单元分治 | ❌ 未实现 |

### 1.3 目标二：CodeAct + MCP

- 明确"计划 -> 执行 -> 观察 -> 调整"的循环结构
- MCP 工具发现、注册、调用桥接
- 工具注册机制同时容纳本地工具与 MCP 工具

### 1.4 目标三：Multi-Agent 协同研究

- 研究主 Agent 与子 Agent 的职责边界
- 评估与现有上下文治理、`CodeAct`、`MCP` 的接入方式

---

## 2. 开发进度

### 2.1 当前阶段

- **日期**：2026-05-02
- **阶段**：上下文治理 A/B/收口已完成，下一步主线为文件级动态装载
- **状态**：Stable Baseline

### 2.2 已完成功能（代码验证确认）

| 模块 | 功能 | 验证 |
|------|------|------|
| Agent 执行 | CodeAct ReAct 循环 | `AbstractAgentExecutor` line 511-657 |
| 上下文治理 | 消息拆分 | `ContextSnapshot` |
| | 预算裁剪 | `ContextBudgetPolicy` 316L |
| | 组装 | `ContextAssembler` 104L |
| | 工具结果摘要化 | `ToolResultContextCompressor` |
| | Artifact 卸载 | `AbstractAgentExecutor.offloadForMemoryIfNeeded()` |
| | 按需回填 | `IndexedRehydrateSelector` 388L |
| | 历史记忆卡片 | `HistoricalContextSummarizer` |
| 上下文收口 | `agent/context` 10 文件 | assembly 6 + compression 3 + token 1 |
| 任务态 | `[Task State]` 卡片 | `TaskExecutionState` 等 |
| 工具 | ShellTool, PythonExecutionTool, BrowserTool 等 | 7 个工具 |
| AI Framework | OpenAI / Anthropic / Gemini | 三家 Provider |
| MCP | 工具发现/注册/调用桥接 | `McpClient`, `McpToolRegistryBootstrap` |

### 2.3 下一步主线：文件级动态装载

**当前基础**：
- `ShellTool` 可执行 `find/rg/head/tail` 发现文件
- `IndexedRehydrateSelector` 已有按需回填机制
- `ContextAssembler` 已有上下文组装能力
- `ContextBudgetPolicy` 已有预算治理能力

**需要补齐**：

| 组件 | 职责 |
|------|------|
| `FileContextDiscovery` | 基于 query/任务状态发现候选文件 |
| `FileFragmentReader` | head/tail/命中片段读取，避免整文件注入 |
| `DynamicContextInjector` | 统一治理文件片段 + artifact 回填 |

**约束**：
- 优先复用现有组件
- 不引入结构化事实记忆、MCP resource 融合、Multi-Agent

---

## 3. 测试情况

### 3.1 验收口径

| 验收项 | 命令 | 状态 |
|--------|------|------|
| 编译 | `./scripts/mvnw-local.sh -q -DskipTests compile` | ✅ |
| 默认测试 | `./scripts/mvnw-local.sh -q -DskipITs test` | ✅ |
| Smoke 测试 | `./scripts/mvnw-local.sh -q -DskipITs -Dgroups=smoke test` | ✅ |
| E2E 测试 | `./scripts/mvnw-local.sh -q -DskipITs -Dgroups=e2e test` | ✅ |
| Live Smoke | `./scripts/run-live-smoke.sh` | ✅ |

### 3.2 测试资产

**Smoke 测试 (7 个)**
- `AgentCoordinatorSmokeTest`
- `BrowserToolSmokeTest`
- `ShellToolSmokeTest`
- `PythonExecutionToolSmokeTest`
- `TaskReflectionToolSmokeTest`
- `SearchToolSmokeTest`
- `WebFetchToolSmokeTest`

**E2E 测试 (3 个)**
- `AgentChatApiE2ETest`
- `ExecutionStreamApiE2ETest`
- `SessionApiE2ETest`

---

## 4. 暂不进入的范围

- 结构化事实记忆
- MCP resource 融合
- Multi-Agent / 子任务上下文分治
