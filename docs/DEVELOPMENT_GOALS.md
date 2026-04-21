# 开发目标

本文档只定义当前阶段的开发目标。

## 1. 总目标

围绕 `aiframework` 构建一个更稳定、可持续演进的 Agent 执行框架，重点推进三项核心能力：

1. 上下文管理能力建设。
2. `CodeAct + MCP` 执行能力建设。
3. `Multi-Agent` 协同能力研究。

## 2. 目标一：上下文管理

目标来源参考：

- [Manus: Context Engineering for AI Agents](https://manus.im/zh-cn/blog/Context-Engineering-for-AI-Agents-Lessons-from-Building-Manus)
- [腾讯云开发者文章](https://cloud.tencent.com/developer/article/2632131)
- [掘金文章](https://juejin.cn/post/7611179521161429046)

### 2.1 目标定义

在当前 `UnifiedAgent + UnifiedWorkflow + ChatMemory` 基础上，补齐“可控上下文工程”能力，而不只是简单存消息历史。

### 2.2 分阶段目标

#### 阶段 A：基础上下文治理

- 明确上下文来源：系统提示、用户输入、历史消息、工具结果、运行态摘要。
- 统一进入模型前的上下文组装顺序。
- 建立消息数量、总量、工具结果体积的基础预算控制。

#### 阶段 B：上下文压缩与回填

- 对超长工具结果进行摘要化、卸载、按需回填。
- 保留重要事实、最近动作和待完成事项，降低无效 token 占用。
- 为多轮任务提供“短期窗口 + 关键记忆”的混合策略。

#### 阶段 B-收口：代码结构精简

- 在阶段 B 两个切片完成后、进入阶段 C 之前，对上下文治理代码进行结构精简。
- 精简范围以 `agent/context` 包为主，目标从 16 个文件精简到 ~11 个，减少 ~350 行。
- 消除 Token 计数层职责重叠（合并近似计数实现、内联编码映射）。
- 消除 `ContextBudgetPolicy` 与 `ModelContextBudgeter` 的 ~60 行重复工具方法。
- 将 `TaskStateBudgetPolicy`（本质只是 5 个常量）合并到 `TaskExecutionState`。
- 精简 `ContextAssembler` 多余构造函数。
- 约束：不改变任何运行时行为，每步精简后完整测试确认回归。

#### 阶段 C：任务态上下文

- 将“计划、执行中间态、待办、失败原因”从普通聊天历史中结构化抽离。
- 支持在单次任务内恢复 agent 执行意图，而不依赖模型重复推断全部上下文。

## 3. 目标二：CodeAct + MCP

目标来源参考：

- [Anthropic: Code execution with MCP](https://www.anthropic.com/engineering/code-execution-with-mcp)

### 3.1 目标定义

在现有工具调用体系上，演进到更明确的 `CodeAct` 执行循环，并逐步支持 `MCP` 工具/资源接入。

### 3.2 分阶段目标

#### 阶段 A：CodeAct 最小闭环

- 明确“计划 -> 执行代码/工具 -> 观察结果 -> 调整计划”的循环结构。
- 统一工具调用结果格式，减少模型解析成本。
- 提升代码执行、文件操作、搜索等工具在同一执行回路里的协同性。

#### 阶段 B：MCP 接入层

- 增加 `MCP client` 适配层。
- 支持 MCP 工具发现、工具注册、调用协议转换。
- 让 `aiframework` 内的工具注册机制可同时容纳本地工具与 MCP 工具。

#### 阶段 C：MCP 资源与会话融合

- 支持资源读取、上下文注入、会话级能力缓存。
- 让 agent 能区分“工具调用”和“资源装载”两类外部能力。

## 4. 推荐推进顺序

建议按以下顺序推进：

1. 先做“上下文治理基础能力”。
2. 再做“CodeAct 最小闭环”。
3. 然后补“上下文压缩/回填”。
4. 最后接入 “MCP client / MCP tool adapter / MCP resource”。

这样可以先稳定 Agent 内核，再扩展外部协议能力。

## 5. 目标三：Multi-Agent 协同研究

### 5.1 目标定义

在单 Agent 最小链路稳定后，研究多 Agent 协同执行模式，包括任务拆分、角色分工、上下文传递与结果汇总等能力。

当前阶段只将 `Multi-Agent` 作为研究内容纳入目标，不把它提升为当前阶段必须落地的默认主线。

### 5.2 研究范围

- 明确主 Agent 与子 Agent 的职责边界，避免多个执行器之间上下文和状态相互渗透。
- 研究任务拆分、调度、结果回收与失败重试的最小协同链路。
- 评估 `Multi-Agent` 与现有上下文治理、`CodeAct`、`MCP` 能力之间的接入方式。
- 优先验证最小可运行编排，不提前引入复杂的分布式调度或通用编排框架。

### 5.3 建议推进前提

- 单 Agent 主链路稳定，当前阶段目标具备可回归验证基础。
- 上下文治理与工具执行边界清晰，能够支撑主子 Agent 之间的最小信息传递。
- `Multi-Agent` 先以研究和设计收敛为主，待单 Agent 阶段收口后再决定是否进入实现阶段。
