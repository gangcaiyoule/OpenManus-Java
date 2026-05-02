package com.openmanus.agent.coordination;

import com.openmanus.agent.base.AbstractAgentExecutor;
import com.openmanus.agent.tool.BrowserTool;
import com.openmanus.agent.tool.PythonExecutionTool;
import com.openmanus.agent.tool.ShellTool;
import com.openmanus.agent.tool.TaskReflectionTool;

/**
 * Agent 执行协调器：
 * - 统一 ReAct 循环
 * - 统一工具注册
 * - 统一系统提示词
 */
public class AgentCoordinator extends AbstractAgentExecutor<AgentCoordinator.Builder> {

    private static final String SYSTEM_PROMPT = """
            你是 OpenManus 的执行协调核心，负责统一调度 Thinking + Search + Code + File + Reflection 的端到端执行。

            ## 工作原则
            1. 优先基于工具获取事实，不臆测本地文件结构、外部信息或执行结果。
            2. 能直接回答的简单问题直接回答；需要外部信息或操作时调用工具。
            3. 工具调用后先读取结果再决定下一步，避免无效重复调用。
            4. 文件操作必须限制在会话沙盒目录内，不请求或尝试越界路径。
            5. 输出要清晰可执行：结论 + 关键依据 + 下一步建议（如需要）。

            ## 可用工具与用途
            - 搜索工具：`search_web`，用于获取网页候选与摘要；搜索只走搜索工具，不打开或展示搜索过程网页。
            - 浏览器操纵工具：`browser_open_url`, `browser_ensure_sandbox`，用于控制前端真实浏览器与会话 VNC 沙箱。
            - 网页抓取工具：`browser_fetch_web`，用于展示目标网页、抓取网页并落本地快照（返回 url + path + preview）。
            - 代码执行工具：`executePython`, `executePythonFile`，用于计算、分析、脚本执行。
            - Shell 工具：`runShellCommand`，用于 find/rg/head/tail/sed 等文件发现与局部读取（建议先发现，再局部读取，最后必要时全文读取）。
            - 反思工具：`recordTask`, `reflectOnTask`, `getTaskHistory`，用于过程记录与复盘。

            ## 执行策略
            - 复杂任务采用“先规划后执行”：先给出简短步骤，再逐步调用工具。
            - 当前默认以统一协调方式组织能力：
              先思考（Thinking）-> 再检索（Search）-> 再操作文件/代码（File/Code）-> 最后复盘（Reflection）。
            - 当工具结果显示失败时，优先调整参数重试一次；仍失败则明确报告阻塞点与替代方案。
            - 涉及代码和文件时，优先最小变更，避免不必要的大范围改动。
            - 查询新闻、赛事进展、价格、法规等可能变化的信息时，先使用 `search_web` 获取最新候选结果；需要打开具体网页再使用 `browser_*` 工具。
            """;

    public static class Builder extends AbstractAgentExecutor.Builder<Builder> {

        private BrowserTool browserTool;
        private PythonExecutionTool pythonExecutionTool;
        private ShellTool shellTool;
        private TaskReflectionTool taskReflectionTool;

        public Builder browserTool(BrowserTool browserTool) {
            this.browserTool = browserTool;
            return this;
        }

        public Builder pythonExecutionTool(PythonExecutionTool pythonExecutionTool) {
            this.pythonExecutionTool = pythonExecutionTool;
            return this;
        }

        public Builder shellTool(ShellTool shellTool) {
            this.shellTool = shellTool;
            return this;
        }

        public Builder taskReflectionTool(TaskReflectionTool taskReflectionTool) {
            this.taskReflectionTool = taskReflectionTool;
            return this;
        }

        public AgentCoordinator build() {
            this.name("agent_coordinator")
                    .description("统一协调搜索、代码、文件与反思工具，完成端到端任务。")
                    .singleParameter("用户请求")
                    .systemMessage(SYSTEM_PROMPT);

            if (browserTool != null) {
                this.toolFromObject(browserTool);
            }
            if (pythonExecutionTool != null) {
                this.toolFromObject(pythonExecutionTool);
            }
            if (shellTool != null) {
                this.toolFromObject(shellTool);
            }
            if (taskReflectionTool != null) {
                this.toolFromObject(taskReflectionTool);
            }

            return new AgentCoordinator(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private AgentCoordinator(Builder builder) {
        super(builder);
    }
}
