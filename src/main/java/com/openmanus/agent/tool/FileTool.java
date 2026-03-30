package com.openmanus.agent.tool;

import com.openmanus.domain.service.SessionSandboxManager;
import com.openmanus.agent.tool.support.SandboxPathResolver;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;

/**
 * 文件操作工具 - 提供完整的文件系统操作能力
 * 
 * 功能：
 * 1. 文件读写操作
 * 2. 目录管理
 * 3. 文件信息查询
 * 
 * 采用模板方法模式统一异常处理
 */
@Component
@Slf4j
public class FileTool {

    private final SandboxPathResolver sandboxPathResolver;

    public FileTool(SessionSandboxManager sessionSandboxManager) {
        this(new SandboxPathResolver(sessionSandboxManager));
    }

    @Autowired
    public FileTool(SandboxPathResolver sandboxPathResolver) {
        this.sandboxPathResolver = sandboxPathResolver;
    }

    @Tool("读取文件内容")
    public String readFile(@P("文件路径") String filePath) {
        return executeFileOperation(filePath, path -> {
            if (!Files.exists(path)) {
                return "文件不存在: " + filePath;
            }
            if (!Files.isReadable(path)) {
                return "文件不可读: " + filePath;
            }
            return "文件内容:\n" + Files.readString(path);
        }, "读取文件");
    }

    @Tool("写入文件内容")
    public String writeFile(@P("文件路径") String filePath, @P("文件内容") String content) {
        if (filePath == null || filePath.isBlank()) {
            return "写入文件失败: 文件路径不能为空";
        }
        String safeContent = content == null ? "" : content;
        return executeFileOperation(filePath, path -> {
            ensureParentExists(path);
            Files.writeString(path, safeContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return "文件写入成功: " + filePath;
        }, "写入文件");
    }

    @Tool("追加文件内容")
    public String appendFile(@P("文件路径") String filePath, @P("追加的内容") String content) {
        if (filePath == null || filePath.isBlank()) {
            return "追加文件失败: 文件路径不能为空";
        }
        String safeContent = content == null ? "" : content;
        return executeFileOperation(filePath, path -> {
            ensureParentExists(path);
            Files.writeString(path, safeContent, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return "内容追加成功: " + filePath;
        }, "追加文件");
    }
    
    @Tool("列出目录内容")
    public String listDirectory(@P("目录路径") String dirPath) {
        return executeFileOperation(dirPath, path -> {
            if (!Files.exists(path)) {
                return "目录不存在: " + dirPath;
            }
            if (!Files.isDirectory(path)) {
                return "不是目录: " + dirPath;
            }
            
            try (Stream<Path> stream = Files.list(path)) {
                String items = stream
                    .sorted()
                    .map(item -> Files.isDirectory(item) 
                        ? "[目录] " + item.getFileName() 
                        : "[文件] " + item.getFileName())
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("(空目录)");
                
                return "目录内容:\n" + items;
            }
        }, "列出目录");
    }

    @Tool("创建目录")
    public String createDirectory(@P("目录路径") String dirPath) {
        return executeFileOperation(dirPath, path -> {
            Files.createDirectories(path);
            return "目录创建成功: " + dirPath;
        }, "创建目录");
    }

    @Tool("删除文件或目录")
    public String deleteFile(@P("文件或目录路径") String targetPath) {
        return "删除操作已禁用：当前为安全模式，仅允许读写和目录浏览。";
    }
    
    @Tool("检查文件是否存在")
    public String fileExists(@P("文件路径") String filePath) {
        try {
            Path path = sandboxPathResolver.resolveSandboxPath(filePath);
            return Files.exists(path) ? "文件存在: " + filePath : "文件不存在: " + filePath;
        } catch (SecurityException e) {
            log.warn("检查文件存在失败: {}", e.getMessage());
            return "检查文件存在失败: " + e.getMessage();
        } catch (RuntimeException e) {
            log.error("检查文件存在失败: {}", filePath, e);
            return "检查文件存在失败: " + e.getMessage();
        }
    }
    
    @Tool("获取文件信息")
    public String getFileInfo(@P("文件路径") String filePath) {
        return executeFileOperation(filePath, path -> {
            if (!Files.exists(path)) {
                return "文件不存在: " + filePath;
            }
            
            return """
                文件信息:
                路径: %s
                大小: %d 字节
                类型: %s
                可读: %s
                可写: %s
                """.formatted(
                    path.toAbsolutePath(),
                    Files.size(path),
                    Files.isDirectory(path) ? "目录" : "文件",
                    Files.isReadable(path) ? "是" : "否",
                    Files.isWritable(path) ? "是" : "否"
                );
        }, "获取文件信息");
    }
    
    /**
     * 模板方法 - 统一异常处理
     */
    private String executeFileOperation(String filePath, FileOperation operation, String operationName) {
        try {
            Path path = sandboxPathResolver.resolveSandboxPath(filePath);
            return operation.execute(path);
        } catch (SecurityException e) {
            log.warn("{}安全检查失败: {}", operationName, e.getMessage());
            return "%s失败: %s".formatted(operationName, e.getMessage());
        } catch (IOException e) {
            log.error("{}失败: {}", operationName, filePath, e);
            return "%s失败: %s".formatted(operationName, e.getMessage());
        } catch (RuntimeException e) {
            log.error("{}失败: {}", operationName, filePath, e);
            return "%s失败: %s".formatted(operationName, e.getMessage());
        }
    }

    /**
     * 确保父目录存在
     */
    private void ensureParentExists(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }
    
    /**
     * 文件操作函数式接口
     */
    @FunctionalInterface
    private interface FileOperation {
        String execute(Path path) throws IOException;
    }
}
