# Review

## 结论

- 结论：**Accepted**
- 阶段状态：**In Progress / Active**
- 结论日期：**2026-04-21**
- 当前判断：阶段 B 两个切片已通过验收，代码结构精简计划已确定，当前进入精简实施阶段。

## 当前有效结论

- 目标一致性：当前实现仍收敛在"单 Agent + 上下文治理阶段 A + CodeAct 阶段 A + 工具结果摘要化/卸载索引/按需回填 + MCP 工具发现/调用桥接"的验收边界内，未扩面到 `Multi-Agent` 默认实现或 MCP 资源融合。
- 分层边界：当前 `domain`/`agent`/`infra`/`aiframework` 职责边界与技术方案一致，未见新的双向渗透或为了未来扩展引入的额外框架层。
- 上下文治理完成状态：阶段 B 两个切片（工具结果摘要化 + 历史关键记忆卡片）均已完成并回归验证。`agent/context` 包共 16 个源文件、2426 行。
- 输入边界与失败语义：`/api/agent/chat` 与 `/api/agent/session/{sessionId}` 仍统一复用 `SessionIdPolicy` 做会话 ID 收口，失败路径返回非成功状态码和稳定 `errorCode`。
- 测试覆盖：当前 `tests=728, failures=0, errors=0, skipped=0`，live smoke `tests=1, failures=0, errors=0, skipped=0`。
- 环境备注：本轮 live smoke 基于显式 `OPENMANUS_LIVE_*` 配置选择 `glm-5.1` 渠道通过。

## 当前有效问题

- 当前 review 未发现新的仓内阻塞问题。

## 代码结构精简评估

### 需要精简的问题

1. **Token 计数层过重 (6 文件, 482L)**：`ApproxModelContextTokenCounter` 与 `TokenizerModelContextTokenCounter` 功能重叠，`ModelTokenizerEncodingMapper` 只被唯一调用方使用。
2. **`ContextBudgetPolicy` 与 `ModelContextBudgeter` 存在 ~60 行重复代码**：`findMessageIndex`、`findLatestToolResultMessage`、`tail`、`sanitize` 工具方法近乎相同。
3. **`TaskStateBudgetPolicy` 过于轻量 (65L)**：本质只是 5 个常量 + getter，不需要独立文件。
4. **`ContextAssembler` 构造器过多**：4 个构造函数为测试灵活性而设，Spring 管理下只需 1 个。

### 不建议精简的部分

- `HistoricalContextSummarizer` (223L)：阶段 B 核心新增，职责清晰。
- `IndexedRehydrateSelector` (388L)：回填规则复杂（中英文命中策略不同），拆分反而更难理解。
- `ToolResultContextCompressor` (315L)：压缩策略核心，逻辑自洽。

### 精简计划

| 优先级 | 操作 | 预期效果 | 风险 |
|--------|------|----------|------|
| P0 | 合并 `TokenizerModelContextTokenCounter` → `ApproxModelContextTokenCounter` | -115L, -1 文件 | 低 |
| P0 | `ModelTokenizerEncodingMapper` 内联到 `ModelTokenizerModelContextTokenCounter` | -83L, -1 文件 | 低 |
| P1 | `ContextBudgetPolicy` + `ModelContextBudgeter` 提取共享工具方法 | -60L 重复 | 低 |
| P1 | `TaskStateBudgetPolicy` 合并到 `TaskExecutionState` | -65L, -1 文件 | 中 |
| P2 | `ContextAssembler` 只保留全参构造函数 | -20L | 低 |

## 最小修正路径

1. 当前优先执行代码结构精简，以 `agent/context` 包为主，按 P0→P1→P2 顺序实施。
2. 每步精简后执行 `./scripts/mvnw-local.sh -q -DskipITs test` 确认无回归。
3. 精简完成后执行完整验收口径（compile + test + live smoke）。
4. 精简收口后再评估是否进入阶段 C、MCP 资源融合或 Multi-Agent 研究阶段。
5. 后续复验继续优先使用显式 `OPENMANUS_LIVE_*` 配置和实际可用模型渠道。
