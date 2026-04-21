package com.openmanus.agent.tool;

import com.openmanus.agent.tool.support.SandboxPathResolver;
import com.openmanus.aiframework.runtime.AiCodeExecutionResult;
import com.openmanus.aiframework.runtime.AiCodeSandbox;
import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
import com.openmanus.aiframework.tool.AiParam;
import com.openmanus.aiframework.tool.AiTool;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Python 代码执行工具
 * 
 * 功能：
 * 1. 在沙箱环境中安全执行 Python 代码
 * 2. 支持代码字符串和文件执行
 * 3. 自动超时控制
 * 
 * 设计模式：模板方法模式 + 策略模式（沙箱/本地执行）
 */
@Slf4j
public class PythonTool {
    
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    
    private final AiCodeSandbox sandbox;
    private final SandboxPathResolver sandboxPathResolver;

    public PythonTool(AiCodeSandbox sandbox, AiSessionSandboxGateway sessionSandboxGateway) {
        this(sandbox, new SandboxPathResolver(sessionSandboxGateway));
    }

    public PythonTool(AiCodeSandbox sandbox, SandboxPathResolver sandboxPathResolver) {
        this.sandbox = sandbox;
        this.sandboxPathResolver = sandboxPathResolver;
    }
    
    /**
     * 执行 Python 代码字符串（在沙箱中）
     */
    @AiTool("在沙箱中执行Python代码")
    public String executePython(
            @AiParam("思考过程或代码计划的简要说明") String thought,
            @AiParam("要执行的Python代码") String code) {
        if (code == null || code.isBlank()) {
            return "执行失败: Python代码不能为空";
        }
        String safeThought = thought == null ? "(empty)" : thought;
        log.info("执行 Python 代码，思考: {}", safeThought);
        log.debug("代码内容: {}", code.length() > 100 ? code.substring(0, 100) + "..." : code);
        
        try {
            // 在沙箱中直接执行代码
            AiCodeExecutionResult result = sandbox.executePython(code, DEFAULT_TIMEOUT_SECONDS);
            return formatExecutionResult(result);
        } catch (Exception e) {
            log.error("Python 代码执行失败", e);
            return "执行失败: " + e.getMessage();
        }
    }
    
    /**
     * 执行 Python 文件（在沙箱中）
     */
    @AiTool("执行Python文件")
    public String executePythonFile(@AiParam("Python文件路径") String filePath) {
        log.info("执行 Python 文件: {}", filePath);
        
        try {
            Path path = resolveSandboxPath(filePath);
            if (!Files.exists(path)) {
                return "文件不存在: " + filePath;
            }
            
            // 读取文件内容并在沙箱中执行
            String code = Files.readString(path, StandardCharsets.UTF_8);
            AiCodeExecutionResult result = sandbox.executePython(code, DEFAULT_TIMEOUT_SECONDS);
            return formatExecutionResult(result);
            
        } catch (SecurityException e) {
            log.warn("Python 文件访问被安全策略拒绝: {}", e.getMessage());
            return "执行失败: " + e.getMessage();
        } catch (Exception e) {
            log.error("Python 文件执行失败: {}", filePath, e);
            return "执行失败: " + e.getMessage();
        }
    }

    /**
     * 将用户路径解析到当前会话沙盒目录，并阻止路径逃逸。
     */
    private Path resolveSandboxPath(String userPath) {
        return sandboxPathResolver.resolveSandboxPath(userPath);
    }
    
    /**
     * 格式化执行结果
     */
    private String formatExecutionResult(AiCodeExecutionResult result) {
        if (result.exitCode() == 0) {
            String output = result.stdout().trim();
            if (output.isEmpty()) {
                return "✅ 执行成功 (无输出)";
            }
            return "✅ 执行成功:\n" + output;
        } else {
            StringBuilder error = new StringBuilder();
            error.append("❌ 执行失败 (退出码: ").append(result.exitCode()).append(")");
            
            if (!result.stderr().isEmpty()) {
                error.append("\n错误信息:\n").append(result.stderr());
            }
            if (!result.stdout().isEmpty()) {
                error.append("\n标准输出:\n").append(result.stdout());
            }
            
            return error.toString();
        }
    }
}
