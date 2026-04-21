package com.openmanus.agent.impl.unified;

import com.openmanus.agent.base.AbstractAgentExecutor;
import com.openmanus.agent.tool.BrowserTool;
import com.openmanus.agent.tool.FileTool;
import com.openmanus.agent.tool.PythonTool;
import com.openmanus.agent.tool.ReflectionTool;

/**
 * 单智能体执行器：
 * - 统一 ReAct 循环
 * - 统一工具注册
 * - 统一系统提示词
 */
public class UnifiedAgent extends AbstractAgentExecutor<UnifiedAgent.Builder> {

    private static final String SYSTEM_PROMPT = """
            你是 OpenManus 的单智能体执行核心，负责在唯一 ReAct 循环内完成 Thinking + Search + Code + File + Reflection 的端到端执行。

            ## 工作原则
            1. 优先基于工具获取事实，不臆测本地文件结构、外部信息或执行结果。
            2. 能直接回答的简单问题直接回答；需要外部信息或操作时调用工具。
            3. 工具调用后先读取结果再决定下一步，避免无效重复调用。
            4. 文件操作必须限制在会话沙盒目录内，不请求或尝试越界路径。
            5. 输出要清晰可执行：结论 + 关键依据 + 下一步建议（如需要）。

            ## 可用工具与用途
            - 浏览搜索工具：`searchWeb`, `browseWeb`，用于最新信息获取与网页内容读取。
            - 代码执行工具：`executePython`, `executePythonFile`，用于计算、分析、脚本执行。
            - 文件工具：`readFile`, `writeFile`, `appendFile`, `listDirectory`, `createDirectory`, `fileExists`, `getFileInfo`。
            - 反思工具：`recordTask`, `reflectOnTask`, `getTaskHistory`，用于过程记录与复盘。

            ## 执行策略
            - 复杂任务采用“先规划后执行”：先给出简短步骤，再逐步调用工具。
            - 把原多智能体职责合并为单体内部分工：
              先思考（Thinking）-> 再检索（Search）-> 再操作文件/代码（File/Code）-> 最后复盘（Reflection）。
            - 当工具结果显示失败时，优先调整参数重试一次；仍失败则明确报告阻塞点与替代方案。
            - 涉及代码和文件时，优先最小变更，避免不必要的大范围改动。
            """;

    public static class Builder extends AbstractAgentExecutor.Builder<Builder> {

        private BrowserTool browserTool;
        private PythonTool pythonTool;
        private FileTool fileTool;
        private ReflectionTool reflectionTool;

        public Builder browserTool(BrowserTool browserTool) {
            this.browserTool = browserTool;
            return this;
        }

        public Builder pythonTool(PythonTool pythonTool) {
            this.pythonTool = pythonTool;
            return this;
        }

        public Builder fileTool(FileTool fileTool) {
            this.fileTool = fileTool;
            return this;
        }

        public Builder reflectionTool(ReflectionTool reflectionTool) {
            this.reflectionTool = reflectionTool;
            return this;
        }

        public UnifiedAgent build() {
            this.name("unified_agent")
                    .description("单智能体模式：统一调用搜索、代码、文件与反思工具，完成端到端任务。")
                    .singleParameter("用户请求")
                    .systemMessage(SYSTEM_PROMPT);

            if (browserTool != null) {
                this.toolFromObject(browserTool);
            }
            if (pythonTool != null) {
                this.toolFromObject(pythonTool);
            }
            if (fileTool != null) {
                this.toolFromObject(fileTool);
            }
            if (reflectionTool != null) {
                this.toolFromObject(reflectionTool);
            }

            return new UnifiedAgent(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private UnifiedAgent(Builder builder) {
        super(builder);
    }
}
