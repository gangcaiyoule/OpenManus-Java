# 开发进度

## 当前阶段状态

- 日期：**2026-04-21**
- 阶段：**上下文治理阶段 B 第二切片已完成，准备进入代码结构精简**
- 状态：**In Progress / Active**
- 当前边界：在"单 Agent + 上下文治理阶段 A + CodeAct 阶段 A + 工具结果摘要化/卸载索引/按需回填 + MCP 工具发现/调用桥接"稳定基线上，已完成上下文治理阶段 B 两个切片（工具结果摘要化 + 历史关键记忆卡片），准备在阶段 B 收口前进行代码结构精简。
- 当前验收口径：`./scripts/mvnw-local.sh -q -DskipTests compile`、`./scripts/mvnw-local.sh -q -DskipITs test`、`./scripts/run-live-smoke.sh`。
- 当前判断：`2026-04-21` 阶段 B 两个切片均已通过验收，代码结构精简计划已确定。当前上下文治理 `agent/context` 包共 16 个源文件、2426 行，存在可精简空间。

## 当前阻塞

- 当前阶段仓内验收阻塞：**无**。
- 环境说明：本轮可用的 OpenAI-compatible 配置为显式 `OPENMANUS_LIVE_*`，模型使用 `glm-5.1`，`./scripts/run-live-smoke.sh` 实际结果为 `tests=1, failures=0, errors=0, skipped=0`。

## 已完成功能

| 模块 | 功能 | 状态 |
|------|------|------|
| Agent 执行 | CodeAct 最小闭环（计划→执行工具→观察→调整），AbstractAgentExecutor 驱动 | 完成 |
| 上下文治理 A | 消息拆分 (Snapshot)、预算裁剪 (BudgetPolicy/Budgeter)、组装 (Assembler) | 完成 |
| 上下文治理 B-1 | 工具结果摘要化、Artifact 卸载索引、按需回填 | 完成 |
| 上下文治理 B-2 | 历史关键记忆卡片 `[Historical Key Memory]` | 完成 |
| Token 计数 | Approx / Lightweight Tokenizer / Model Tokenizer 三级策略 + Factory | 完成 |
| 任务态注入 | `[Task State]` 卡片渲染，追踪 plan/todo/inProgress/lastFailure | 完成 |
| 工具 | FileTool, PythonTool, BrowserTool, ReflectionTool | 完成 |
| AI Framework | OpenAI / Anthropic / Gemini 三家 Provider，HTTP + SSE 传输 | 完成 |
| MCP | 工具发现、注册、调用桥接 | 完成 |
| Web 层 | AgentController (chat/stream/session)、WebSocket 心跳、Web 代理、监控 | 完成 |
| 沙箱 | Docker 代码沙箱、VNC 沙箱、Session 级文件沙箱 | 完成 |
| 记忆 | 对话记忆（内存/文件持久化）、工具结果 Artifact 存储 | 完成 |
| 安全 | SessionIdPolicy 统一校验、Web 代理目标白名单、错误脱敏 | 完成 |

## 代码文件结构

### 总体分层

```
src/main/java/com/openmanus/
├── StartupBanner.java, WebApplication.java
│
├── agent/                          ← Agent 执行层
│   ├── base/                       AbstractAgent, AbstractAgentExecutor (1236L)
│   ├── context/                    上下文治理 (16 文件, 2426L)
│   ├── impl/unified/               UnifiedAgent
│   ├── tool/                       FileTool, PythonTool, BrowserTool, ReflectionTool
│   └── workflow/                   UnifiedWorkflow
│
├── aiframework/                    ← AI 框架抽象层
│   ├── api/                        AiProviderClient, StreamListener
│   ├── assembler/                  OpenAI/Anthropic/Gemini 请求组装
│   ├── client/                     OpenAI/Anthropic/Gemini 客户端
│   ├── config/                     AiProviderClientRegistry
│   ├── model/                      消息/响应/配置 数据模型
│   ├── parser/                     OpenAI/Anthropic/Gemini 响应解析
│   ├── runtime/                    ChatModel, Memory, Sandbox, MCP 等
│   │   ├── model/                  AiChatMessage/Request/Response/ToolSpec 等
│   │   └── mcp/                    McpClient, McpTool*, StubMcpClient
│   ├── tool/                       AiTool 注册/执行
│   │   └── mcp/                    MCP 工具桥接
│   └── transport/                  HttpTransport, SseTransport
│
├── domain/                         ← 业务语义层 (port + service)
│   ├── model/                      WorkflowRequest/Response, SessionSandboxInfo 等
│   └── service/                    AgentService, WorkflowStreamService, ports 等
│
└── infra/                          ← 基础设施层 (Spring 装配 + 运行时)
    ├── config/                     14 文件: AiFrameworkConfig, DomainServiceConfig 等
    ├── exception/                  OpenManusException, TokenLimitExceededException
    ├── log/                        WebSocket 日志推送
    ├── memory/                     ChatMemory, ArtifactStore 实现
    ├── monitoring/                 执行追踪, WebSocket 事件推送
    ├── sandbox/                    Docker/VNC 沙箱适配器
    ├── web/                        Controller, WebProxy, WebSocket
    └── workflow/                   UnifiedWorkflowPortAdapter
```

### 上下文治理详细结构 (`agent/context/`, 16 文件, 2426L)

| 组 | 文件 | 行数 | 职责 |
|----|------|------|------|
| **核心管道** | ContextSnapshot | 64L | 不可变消息快照，拆分历史/当前轮 |
| | ContextAssembler | 129L | 组装编排器：历史→当前轮→预算裁剪→压缩→注入 |
| | ContextBudgetPolicy | 191L | 消息数量预算（历史/总量限制） |
| | ModelContextBudgeter | 187L | Token 预算选择器，保锚点+填最新 |
| **Token 计数** | ModelContextTokenCounter | 24L | Token 计数接口 |
| | ApproxModelContextTokenCounter | 44L | 近似计数 (char/4) |
| | TokenizerModelContextTokenCounter | 115L | Unicode 感知轻量计数 |
| | ModelTokenizerModelContextTokenCounter | 94L | jtokkit 真实分词计数 |
| | ModelContextTokenCounterFactory | 122L | 计数器工厂（模式选择+fallback） |
| | ModelTokenizerEncodingMapper | 83L | 模型名→编码映射 |
| **任务态** | TaskExecutionState | 167L | 不可变任务态载体 (plan/todo/inProgress/lastFailure) |
| | TaskExecutionStateTracker | 143L | 从助手消息和工具结果追踪状态 |
| | TaskStateBudgetPolicy | 65L | 任务态字段预算限制 |
| | TaskStateContextInjector | 72L | 渲染 [Task State] 卡片注入上下文 |
| **压缩/回填** | ToolResultContextCompressor | 315L | 超长工具结果→摘要卡片 |
| | IndexedRehydrateSelector | 388L | 按需回填 artifact |
| | HistoricalContextSummarizer | 223L | 被裁历史→[Historical Key Memory] 卡片 |

### 测试覆盖

- 源文件：~110 个 Java 源文件
- 测试文件：96 个测试文件
- 当前测试结果：`tests=728, failures=0, errors=0, skipped=0`

## 当前主线

1. 当前主线基线稳定：`compile`、默认 `test` 与 `live smoke` 都已通过。
2. 上下文治理阶段 B 两个切片均已完成并回归验证。
3. 下一步将进行代码结构精简，精简范围以 `agent/context` 包为主，确保不改变运行时行为。

## 下一步：代码结构精简

### 背景

当前 `agent/context` 包在阶段 B 演进过程中自然积累了 16 个源文件，部分存在职责重叠和代码重复，需在阶段 B 收口前精简。

### 精简计划

| 优先级 | 操作 | 预期效果 | 风险 |
|--------|------|----------|------|
| P0 | 合并 `TokenizerModelContextTokenCounter` → `ApproxModelContextTokenCounter`，统一为一个更好的估算公式 | -115L, -1 文件 | 低 |
| P0 | `ModelTokenizerEncodingMapper` 内联到 `ModelTokenizerModelContextTokenCounter` | -83L, -1 文件 | 低 |
| P1 | `ContextBudgetPolicy` + `ModelContextBudgeter` 提取共享工具方法（`findMessageIndex`, `tail`, `sanitize` 等 ~60L 重复代码） | -60L 重复 | 低 |
| P1 | `TaskStateBudgetPolicy` 合并到 `TaskExecutionState` 作为内部常量 | -65L, -1 文件 | 中 |
| P2 | `ContextAssembler` 删除 3 个多余构造函数，只保留全参构造函数 | -20L | 低 |

### 精简目标

- 文件数：16 → ~11 个
- 总行数：减少 ~350 行
- 不改变任何运行时行为和外部可观测语义
- 每步精简后执行完整测试套件确认回归

### 精简后继续

- 继续在阶段 B 内评估是否需要"结构化重要事实提炼"
- 精简收口后再评估是否进入阶段 C、MCP 资源融合或 Multi-Agent 研究阶段
