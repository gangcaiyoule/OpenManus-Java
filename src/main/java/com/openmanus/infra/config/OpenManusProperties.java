package com.openmanus.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OpenManus Configuration Properties
 *
 * Centralized configuration management for OpenManus project
 * Supports configuration from Spring config files and environment variables
 */
@Data
@Slf4j
@ConfigurationProperties(prefix = "openmanus")
public class OpenManusProperties {
    
    /**
     * Application basic configuration
     */
    @NestedConfigurationProperty
    private AppConfig app = new AppConfig();
    
    /**
     * LLM service configuration
     */
    @NestedConfigurationProperty
    private LlmConfig llm = new LlmConfig();
    
    /**
     * Sandbox environment configuration
     */
    @NestedConfigurationProperty
    private SandboxSettings sandbox = new SandboxSettings();
    
    /**
     * Browser automation configuration
     */
    @NestedConfigurationProperty
    private BrowserConfig browser = new BrowserConfig();
    
    /**
     * Proxy configuration
     */
    @NestedConfigurationProperty
    private ProxyConfig proxy = new ProxyConfig();
    
    /**
     * Search engine configuration
     */
    @NestedConfigurationProperty
    private SearchConfig search = new SearchConfig();

    /**
     * Web proxy exposure configuration.
     */
    @NestedConfigurationProperty
    private WebProxyConfig webProxy = new WebProxyConfig();
    
    /**
     * Runflow configuration
     */
    @NestedConfigurationProperty
    private RunflowConfig runflow = new RunflowConfig();

    /**
     * Chat memory configuration
     */
    @NestedConfigurationProperty
    private ChatMemoryConfig chatMemory = new ChatMemoryConfig();

    /**
     * MCP runtime configuration.
     */
    @NestedConfigurationProperty
    private McpConfig mcp = new McpConfig();

    /**
     * Legacy mapping logging configuration.
     */
    @NestedConfigurationProperty
    private LegacyMappingConfig legacyMapping = new LegacyMappingConfig();

    @PostConstruct
    void applyEnvFallbacks() {
        if (app != null) {
            String defaultUserId = firstNonBlankEnv(
                    "OPENMANUS_APP_DEFAULT_USER_ID",
                    "OPENMANUS_APP_DEFAULTUSERID",
                    "USER_ID"
            );
            if (!isBlank(defaultUserId)) {
                app.setDefaultUserId(defaultUserId);
            }
        }

        if (llm != null && llm.getDefaultLlm() != null) {
            if (isBlank(llm.getDefaultLlm().getApiType())) {
                String apiType = firstNonBlankEnv(
                        "OPENMANUS_LLM_DEFAULT_LLM_API_TYPE",
                        "OPENMANUS_LLM_DEFAULTLLM_APITYPE",
                        "OPENAI_API_TYPE"
                );
                if (!isBlank(apiType)) {
                    llm.getDefaultLlm().setApiType(apiType);
                }
            }

            if (isBlank(llm.getDefaultLlm().getBaseUrl())) {
                String baseUrl = firstNonBlankEnv(
                        "OPENMANUS_LLM_DEFAULT_LLM_BASE_URL",
                        "OPENMANUS_LLM_DEFAULTLLM_BASEURL",
                        "OPENAI_BASE_URL"
                );
                if (!isBlank(baseUrl)) {
                    llm.getDefaultLlm().setBaseUrl(baseUrl);
                }
            }

            String apiKey = llm.getDefaultLlm().getApiKey();
            if (isBlank(apiKey)) {
                String envKey = firstNonBlankEnv(
                        "OPENMANUS_LLM_DEFAULT_LLM_API_KEY",
                        "OPENMANUS_LLM_DEFAULTLLM_APIKEY",
                        "OPENAI_API_KEY"
                );
                if (!isBlank(envKey)) {
                    llm.getDefaultLlm().setApiKey(envKey);
                }
            }

            if (isBlank(llm.getDefaultLlm().getModel())) {
                String model = firstNonBlankEnv(
                        "OPENMANUS_LLM_DEFAULT_LLM_MODEL",
                        "OPENMANUS_LLM_DEFAULTLLM_MODEL",
                        "OPENAI_MODEL"
                );
                if (!isBlank(model)) {
                    llm.getDefaultLlm().setModel(model);
                }
            }
        }

        if (search != null) {
            String apiKey = search.getApiKey();
            if (isBlank(apiKey) || isPlaceholder(apiKey)) {
                String envKey = firstNonBlankEnv(
                        "OPENMANUS_SEARCH_API_KEY",
                        "OPENMANUS_SEARCH_APIKEY",
                        "SERPER_API_KEY",
                        "SERPER_APIKEY"
                );
                if (!isBlank(envKey)) {
                    search.setApiKey(envKey);
                }
            }
        }

        if (sandbox != null) {
            String image = firstNonBlankEnv(
                    "OPENMANUS_SANDBOX_IMAGE"
            );
            if (!isBlank(image)) {
                sandbox.setImage(image);
            }

            String workDir = firstNonBlankEnv(
                    "OPENMANUS_SANDBOX_WORK_DIR",
                    "OPENMANUS_SANDBOX_WORKDIR"
            );
            if (!isBlank(workDir)) {
                sandbox.setWorkDir(workDir);
            }

            String memoryLimit = firstNonBlankEnv(
                    "OPENMANUS_SANDBOX_MEMORY_LIMIT",
                    "OPENMANUS_SANDBOX_MEMORYLIMIT"
            );
            if (!isBlank(memoryLimit)) {
                sandbox.setMemoryLimit(memoryLimit);
            }

            String cpuLimit = firstNonBlankEnv(
                    "OPENMANUS_SANDBOX_CPU_LIMIT",
                    "OPENMANUS_SANDBOX_CPULIMIT"
            );
            if (!isBlank(cpuLimit)) {
                sandbox.setCpuLimit(parsePositiveDouble(cpuLimit, sandbox.getCpuLimit()));
            }

            String timeout = firstNonBlankEnv(
                    "OPENMANUS_SANDBOX_TIMEOUT"
            );
            if (!isBlank(timeout)) {
                sandbox.setTimeout(parsePositiveInt(timeout, sandbox.getTimeout()));
            }

            String networkEnabled = firstNonBlankEnv(
                    "OPENMANUS_SANDBOX_NETWORK_ENABLED",
                    "OPENMANUS_SANDBOX_NETWORKENABLED"
            );
            if (!isBlank(networkEnabled)) {
                sandbox.setNetworkEnabled(parseBooleanStrict(networkEnabled, sandbox.isNetworkEnabled()));
            }
        }

        if (webProxy != null) {
            if (webProxy.enabled == null) {
                String enabled = firstNonBlankEnv("OPENMANUS_WEB_PROXY_ENABLED");
                if (!isBlank(enabled)) {
                    webProxy.setEnabled(parseBooleanStrict(enabled, webProxy.isEnabled()));
                }
            }
            if (webProxy.allowedOrigins == null || webProxy.allowedOrigins.isEmpty()) {
                String allowedOrigins = firstNonBlankEnv("OPENMANUS_WEB_PROXY_ALLOWED_ORIGINS");
                if (!isBlank(allowedOrigins)) {
                    webProxy.setAllowedOrigins(parseCsvList(allowedOrigins));
                }
            }
        }

        if (chatMemory != null) {
            if (isBlank(chatMemory.getStoreType())) {
                String storeType = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_STORE_TYPE");
                if (!isBlank(storeType)) {
                    chatMemory.setStoreType(storeType.trim().toLowerCase(Locale.ROOT));
                }
            }
            if (shouldApplyChatMemoryFileStoreDirFallback(chatMemory)) {
                String dir = firstNonBlankEnv(
                        "OPENMANUS_CHAT_MEMORY_FILE_STORE_DIR",
                        "OPENMANUS_CHATMEMORY_FILESTOREDIR"
                );
                if (!isBlank(dir)) {
                    chatMemory.setFileStoreDir(dir);
                }
            }
            if (chatMemory.retentionDays == null) {
                String retentionDays = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_RETENTION_DAYS");
                if (!isBlank(retentionDays)) {
                    chatMemory.setRetentionDays(parsePositiveInt(retentionDays, chatMemory.getRetentionDays()));
                }
            }
            if (chatMemory.quarantineCorruptedFiles == null) {
                String quarantine = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_QUARANTINE_CORRUPTED_FILES");
                if (!isBlank(quarantine)) {
                    chatMemory.setQuarantineCorruptedFiles(parseBooleanStrict(
                            quarantine, chatMemory.isQuarantineCorruptedFiles()));
                }
            }
            if (chatMemory.modelContextMaxMessages == null) {
                String contextMaxMessages = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_MODEL_CONTEXT_MAX_MESSAGES");
                if (!isBlank(contextMaxMessages)) {
                    chatMemory.setModelContextMaxMessages(parseNonNegativeInt(
                            contextMaxMessages, chatMemory.getModelContextMaxMessages()));
                }
            }
            if (chatMemory.modelContextMaxTotalMessages == null) {
                String totalContextMaxMessages = firstNonBlankEnv(
                        "OPENMANUS_CHAT_MEMORY_MODEL_CONTEXT_MAX_TOTAL_MESSAGES");
                if (!isBlank(totalContextMaxMessages)) {
                    chatMemory.setModelContextMaxTotalMessages(parseNonNegativeInt(
                            totalContextMaxMessages, chatMemory.getModelContextMaxTotalMessages()));
                }
            }
            if (chatMemory.modelContextMaxApproxTokens == null) {
                String approxTokens = firstNonBlankEnv(
                        "OPENMANUS_CHAT_MEMORY_MODEL_CONTEXT_MAX_APPROX_TOKENS");
                if (!isBlank(approxTokens)) {
                    chatMemory.setModelContextMaxApproxTokens(parseNonNegativeInt(
                            approxTokens, chatMemory.getModelContextMaxApproxTokens()));
                }
            }
            if (isBlank(chatMemory.getModelContextTokenCountModeRaw())) {
                String countMode = firstNonBlankEnv(
                        "OPENMANUS_CHAT_MEMORY_MODEL_CONTEXT_TOKEN_COUNT_MODE");
                if (!isBlank(countMode)) {
                    chatMemory.setModelContextTokenCountMode(countMode);
                }
            }
            if (chatMemory.reactMaxIterations == null) {
                String reactMaxIterations = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_REACT_MAX_ITERATIONS");
                if (!isBlank(reactMaxIterations)) {
                    chatMemory.setReactMaxIterations(parseNonNegativeInt(
                            reactMaxIterations, chatMemory.getReactMaxIterations()));
                }
            }
            if (chatMemory.reactMaxExecutionSeconds == null) {
                String reactMaxExecutionSeconds = firstNonBlankEnv(
                        "OPENMANUS_CHAT_MEMORY_REACT_MAX_EXECUTION_SECONDS");
                if (!isBlank(reactMaxExecutionSeconds)) {
                    chatMemory.setReactMaxExecutionSeconds(parseNonNegativeInt(
                            reactMaxExecutionSeconds, chatMemory.getReactMaxExecutionSeconds()));
                }
            }
            if (chatMemory.reactRepeatedToolCallThreshold == null) {
                String threshold = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_REACT_REPEATED_TOOL_CALL_THRESHOLD");
                if (!isBlank(threshold)) {
                    chatMemory.setReactRepeatedToolCallThreshold(parseNonNegativeInt(
                            threshold, chatMemory.getReactRepeatedToolCallThreshold()));
                }
            }
            if (chatMemory.taskStatePlanMaxChars == null) {
                String planMaxChars = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_TASK_STATE_PLAN_MAX_CHARS");
                if (!isBlank(planMaxChars)) {
                    chatMemory.setTaskStatePlanMaxChars(parsePositiveInt(
                            planMaxChars, chatMemory.getTaskStatePlanMaxChars()));
                }
            }
            if (chatMemory.taskStateInProgressMaxChars == null) {
                String inProgressMaxChars = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_TASK_STATE_IN_PROGRESS_MAX_CHARS");
                if (!isBlank(inProgressMaxChars)) {
                    chatMemory.setTaskStateInProgressMaxChars(parsePositiveInt(
                            inProgressMaxChars, chatMemory.getTaskStateInProgressMaxChars()));
                }
            }
            if (chatMemory.taskStateLastFailureMaxChars == null) {
                String lastFailureMaxChars = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_TASK_STATE_LAST_FAILURE_MAX_CHARS");
                if (!isBlank(lastFailureMaxChars)) {
                    chatMemory.setTaskStateLastFailureMaxChars(parsePositiveInt(
                            lastFailureMaxChars, chatMemory.getTaskStateLastFailureMaxChars()));
                }
            }
            if (chatMemory.taskStateTodoMaxItems == null) {
                String todoMaxItems = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_TASK_STATE_TODO_MAX_ITEMS");
                if (!isBlank(todoMaxItems)) {
                    chatMemory.setTaskStateTodoMaxItems(parsePositiveInt(
                            todoMaxItems, chatMemory.getTaskStateTodoMaxItems()));
                }
            }
            if (chatMemory.taskStateTodoItemMaxChars == null) {
                String todoItemMaxChars = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_TASK_STATE_TODO_ITEM_MAX_CHARS");
                if (!isBlank(todoItemMaxChars)) {
                    chatMemory.setTaskStateTodoItemMaxChars(parsePositiveInt(
                            todoItemMaxChars, chatMemory.getTaskStateTodoItemMaxChars()));
                }
            }
            if (chatMemory.toolResultBudgetEnabled == null) {
                String budgetEnabled = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_TOOL_RESULT_BUDGET_ENABLED");
                if (!isBlank(budgetEnabled)) {
                    chatMemory.setToolResultBudgetEnabled(parseBooleanStrict(
                            budgetEnabled, chatMemory.isToolResultBudgetEnabled()));
                }
            }
            if (chatMemory.toolResultBudgetMinChars == null) {
                String minChars = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_TOOL_RESULT_BUDGET_MIN_CHARS");
                if (!isBlank(minChars)) {
                    chatMemory.setToolResultBudgetMinChars(parsePositiveInt(
                            minChars, chatMemory.getToolResultBudgetMinChars()));
                }
            }
            if (chatMemory.toolResultBudgetPreviewHeadChars == null) {
                String headChars = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_TOOL_RESULT_BUDGET_PREVIEW_HEAD_CHARS");
                if (!isBlank(headChars)) {
                    chatMemory.setToolResultBudgetPreviewHeadChars(parsePositiveInt(
                            headChars, chatMemory.getToolResultBudgetPreviewHeadChars()));
                }
            }
            if (chatMemory.toolResultBudgetPreviewTailChars == null) {
                String tailChars = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_TOOL_RESULT_BUDGET_PREVIEW_TAIL_CHARS");
                if (!isBlank(tailChars)) {
                    chatMemory.setToolResultBudgetPreviewTailChars(parsePositiveInt(
                            tailChars, chatMemory.getToolResultBudgetPreviewTailChars()));
                }
            }
            if (chatMemory.toolResultBudgetDecayChars == null) {
                String decayChars = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_TOOL_RESULT_BUDGET_DECAY_CHARS");
                if (!isBlank(decayChars)) {
                    chatMemory.setToolResultBudgetDecayChars(parseNonNegativeInt(
                            decayChars, chatMemory.getToolResultBudgetDecayChars()));
                }
            }
            if (chatMemory.shellToolEnabled == null) {
                String shellEnabled = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_SHELL_TOOL_ENABLED");
                if (!isBlank(shellEnabled)) {
                    chatMemory.setShellToolEnabled(parseBooleanStrict(shellEnabled, chatMemory.isShellToolEnabled()));
                }
            }
            if (chatMemory.shellToolTimeoutSeconds == null) {
                String shellTimeout = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_SHELL_TOOL_TIMEOUT_SECONDS");
                if (!isBlank(shellTimeout)) {
                    chatMemory.setShellToolTimeoutSeconds(parsePositiveInt(shellTimeout, chatMemory.getShellToolTimeoutSeconds()));
                }
            }
            if (chatMemory.getModelContextMaxTotalMessages() == 1) {
                log.warn("openmanus.chat-memory.model-context-max-total-messages=1 is an extreme mode; "
                        + "current user continuity is prioritized and system/history may be dropped for that round.");
            }
        }

        if (mcp != null) {
            if (mcp.enabled == null) {
                String enabled = firstNonBlankEnv("OPENMANUS_MCP_ENABLED");
                if (!isBlank(enabled)) {
                    mcp.setEnabled(parseBooleanStrict(enabled, mcp.isEnabled()));
                }
            }
        }

        if (legacyMapping != null) {
            if (legacyMapping.warnEnabled == null) {
                String warnEnabled = firstNonBlankEnv("OPENMANUS_LEGACY_MAPPING_WARN_ENABLED");
                if (!isBlank(warnEnabled)) {
                    legacyMapping.setWarnEnabled(parseBooleanStrict(warnEnabled, legacyMapping.isWarnEnabled()));
                }
            }
            if (legacyMapping.warnSampleRate == null) {
                String warnSampleRate = firstNonBlankEnv("OPENMANUS_LEGACY_MAPPING_WARN_SAMPLE_RATE");
                if (!isBlank(warnSampleRate)) {
                    legacyMapping.setWarnSampleRate(parsePositiveInt(
                            warnSampleRate, legacyMapping.getWarnSampleRate()));
                }
            }
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static boolean isPlaceholder(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        String lower = s.toLowerCase(Locale.ROOT);
        return lower.startsWith("your-")
                || lower.contains("placeholder")
                || lower.contains("<your")
                || lower.contains("your-api-key");
    }

    private static String firstNonBlankEnv(String... names) {
        for (String name : names) {
            String value = System.getProperty(name);
            if (!isBlank(value)) {
                return value.trim();
            }
            value = System.getenv(name);
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static int parsePositiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseNonNegativeInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parsePositiveDouble(String value, double fallback) {
        try {
            double parsed = Double.parseDouble(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean parseBooleanStrict(String value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        if ("true".equalsIgnoreCase(trimmed)) {
            return true;
        }
        if ("false".equalsIgnoreCase(trimmed)) {
            return false;
        }
        return fallback;
    }

    private static List<String> parseCsvList(String value) {
        if (isBlank(value)) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (String item : value.split(",")) {
            String trimmed = item == null ? "" : item.trim();
            if (!trimmed.isEmpty()) {
                items.add(trimmed);
            }
        }
        return Collections.unmodifiableList(items);
    }

    private static boolean shouldApplyChatMemoryFileStoreDirFallback(ChatMemoryConfig chatMemory) {
        if (chatMemory == null) {
            return false;
        }
        String dir = chatMemory.getFileStoreDir();
        if (isBlank(dir)) {
            return true;
        }
        return ChatMemoryConfig.DEFAULT_FILE_STORE_DIR.equals(dir);
    }

    /**
     * Application basic configuration
     */
    @Data
    public static class AppConfig {
        private String name = "OpenManus";
        private String version = "1.0.0";
        private String workspaceRoot = "./workspace";
        private String logLevel = "INFO";
        private String defaultUserId = "001";
    }
    
    /**
     * LLM service configuration
     */
    @Data
    public static class LlmConfig {
        @NestedConfigurationProperty
        private DefaultLLM defaultLlm = new DefaultLLM();
        private Map<String, ProviderProfile> providers = new LinkedHashMap<>();
        
        @Data
        public static class DefaultLLM {
            private String model = "";
            private String baseUrl = "";
            private String apiType = "";
            private Double temperature = 0.7;
            private Integer maxTokens = 8192;
            private Integer timeout = 120;
            private String apiKey = "";
        }

        @Data
        public static class ProviderProfile {
            private String apiType = "";
            private String model = "";
            private String baseUrl = "";
            private String apiKey = "";
            private Integer timeout = 120;
            private Integer maxRetries = 1;
        }
    }
    
    /**
     * Sandbox configuration
     */
    @Data
    public static class SandboxSettings {
        private String image = "python:3.9-slim";
        private String workDir = "/workspace";
        private String memoryLimit = "512m";
        private double cpuLimit = 1.0;
        private int timeout = 30;
        private boolean networkEnabled = false;
    }
    
    /**
     * Browser automation configuration
     */
    @Data
    public static class BrowserConfig {
        /**
         * Browser type (chrome, firefox, safari)
         */
        private String type = "chrome";
        
        /**
         * Whether to run in headless mode
         */
        private boolean headless = true;
        
        /**
         * Browser operation timeout (seconds)
         */
        private int timeout = 30;
        
        /**
         * User agent string
         */
        private String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    }
    
    /**
     * Proxy configuration
     */
    @Data
    public static class ProxyConfig {
        private boolean enabled = false;
        private String httpProxy = "";
        private String httpsProxy = "";
    }
    
    /**
     * Search engine configuration
     */
    @Data
    public static class SearchConfig {
        /**
         * Search engine type: serper, google, bing
         */
        private String engine = "serper";
        
        /**
         * API key for the search engine
         */
        private String apiKey = "";
        
        /**
         * Maximum number of search results
         */
        private int maxResults = 10;
        
        /**
         * Serper API endpoint
         */
        private String serperEndpoint = "https://google.serper.dev/search";
    }

    /**
     * Web proxy exposure configuration.
     */
    @Data
    public static class WebProxyConfig {
        private Boolean enabled;
        private List<String> allowedOrigins = List.of();

        public boolean isEnabled() {
            return Boolean.TRUE.equals(enabled);
        }
    }
    
    /**
     * Runflow configuration
     */
    @Data
    public static class RunflowConfig {
        private boolean enabled = false;
        private int maxSteps = 50;      // 增加到 50 步（原：20 步）
        private int timeout = 600;      // 增加到 600 秒（原：300 秒）
    }

    /**
     * Chat memory configuration.
     */
    @Data
    public static class ChatMemoryConfig {
        static final String DEFAULT_FILE_STORE_DIR =
                System.getProperty("java.io.tmpdir") + "/openmanus/chat-memory";
        private static final int DEFAULT_RETENTION_DAYS = 30;
        private static final boolean DEFAULT_QUARANTINE_CORRUPTED_FILES = true;
        private static final int DEFAULT_MODEL_CONTEXT_MAX_MESSAGES = 0;
        private static final int DEFAULT_MODEL_CONTEXT_MAX_TOTAL_MESSAGES = 0;
        private static final int DEFAULT_MODEL_CONTEXT_MAX_APPROX_TOKENS = 128000;
        private static final String DEFAULT_MODEL_CONTEXT_TOKEN_COUNT_MODE = "approx";
        private static final int DEFAULT_REACT_MAX_ITERATIONS = 0;
        private static final int DEFAULT_REACT_MAX_EXECUTION_SECONDS = 0;
        private static final int DEFAULT_REACT_REPEATED_TOOL_CALL_THRESHOLD = 0;
        private static final int DEFAULT_TASK_STATE_PLAN_MAX_CHARS = 240;
        private static final int DEFAULT_TASK_STATE_IN_PROGRESS_MAX_CHARS = 120;
        private static final int DEFAULT_TASK_STATE_LAST_FAILURE_MAX_CHARS = 240;
        private static final int DEFAULT_TASK_STATE_TODO_MAX_ITEMS = 6;
        private static final int DEFAULT_TASK_STATE_TODO_ITEM_MAX_CHARS = 120;
        private static final boolean DEFAULT_TOOL_RESULT_BUDGET_ENABLED = true;
        private static final int DEFAULT_TOOL_RESULT_BUDGET_MIN_CHARS = 12000;
        private static final int DEFAULT_TOOL_RESULT_BUDGET_PREVIEW_HEAD_CHARS = 240;
        private static final int DEFAULT_TOOL_RESULT_BUDGET_PREVIEW_TAIL_CHARS = 160;
        private static final int DEFAULT_TOOL_RESULT_BUDGET_DECAY_CHARS = 0;
        private static final boolean DEFAULT_SHELL_TOOL_ENABLED = true;
        private static final int DEFAULT_SHELL_TOOL_TIMEOUT_SECONDS = 15;

        /**
         * Chat memory store type: file | in-memory.
         */
        private String storeType = "";
        /**
         * File store root directory.
         * Full message history of each conversation is persisted under this path.
         */
        private String fileStoreDir = DEFAULT_FILE_STORE_DIR;
        /**
         * Expired memory file cleanup window in days.
         */
        private Integer retentionDays = null;
        /**
         * Move corrupted JSON files into quarantine directory before failing read.
         */
        private Boolean quarantineCorruptedFiles = null;
        /**
         * Max number of historical messages sent to model each round.
         * 0 means unlimited.
         */
        private Integer modelContextMaxMessages = null;
        /**
         * Max total messages sent to model each round.
         * 0 means unlimited.
         * 1 means keep only the most critical message in current turn (typically current user message).
         */
        private Integer modelContextMaxTotalMessages = null;
        /**
         * Max approximate token budget sent to model each round.
         * 0 means unlimited.
         */
        private Integer modelContextMaxApproxTokens = null;
        /**
         * Token-count mode used by model-context budget.
         * Supported values: approx | tokenizer.
         */
        private String modelContextTokenCountMode = "";
        /**
         * Max ReAct loop iterations for one execute. 0 means unlimited.
         */
        private Integer reactMaxIterations = null;
        /**
         * Max ReAct execution seconds for one execute. 0 means unlimited.
         */
        private Integer reactMaxExecutionSeconds = null;
        /**
         * Abort when the exact same tool-call batch repeats over threshold.
         * 0 means disabled.
         */
        private Integer reactRepeatedToolCallThreshold = null;
        /**
         * Max chars for task-state plan field in context card.
         */
        private Integer taskStatePlanMaxChars = null;
        /**
         * Max chars for task-state in-progress field in context card.
         */
        private Integer taskStateInProgressMaxChars = null;
        /**
         * Max chars for task-state last-failure field in context card.
         */
        private Integer taskStateLastFailureMaxChars = null;
        /**
         * Max todo items allowed in task-state context card.
         */
        private Integer taskStateTodoMaxItems = null;
        /**
         * Max chars for each todo item in task-state context card.
         */
        private Integer taskStateTodoItemMaxChars = null;
        /**
         * Whether to replace oversized tool results with explicit sandbox file stubs before model API calls.
         */
        private Boolean toolResultBudgetEnabled = null;
        /**
         * Minimum chars to trigger tool-result budget offload.
         */
        private Integer toolResultBudgetMinChars = null;
        /**
         * Leading preview chars kept in the tool-result stub.
         */
        private Integer toolResultBudgetPreviewHeadChars = null;
        /**
         * Trailing preview chars kept in the tool-result stub.
         */
        private Integer toolResultBudgetPreviewTailChars = null;
        /**
         * Optional context-decay chars threshold. 0 disables decay-triggered offload.
         */
        private Integer toolResultBudgetDecayChars = null;
        /**
         * Whether to enable generic shell tool for model-driven file discovery and partial reads.
         */
        private Boolean shellToolEnabled = null;
        /**
         * Timeout seconds for one shell command execution.
         */
        private Integer shellToolTimeoutSeconds = null;
        public int getRetentionDays() {
            return retentionDays == null ? DEFAULT_RETENTION_DAYS : retentionDays;
        }

        public boolean isQuarantineCorruptedFiles() {
            return quarantineCorruptedFiles == null ? DEFAULT_QUARANTINE_CORRUPTED_FILES : quarantineCorruptedFiles;
        }

        public int getModelContextMaxMessages() {
            return modelContextMaxMessages == null
                    ? DEFAULT_MODEL_CONTEXT_MAX_MESSAGES
                    : modelContextMaxMessages;
        }

        public int getModelContextMaxTotalMessages() {
            return modelContextMaxTotalMessages == null
                    ? DEFAULT_MODEL_CONTEXT_MAX_TOTAL_MESSAGES
                    : modelContextMaxTotalMessages;
        }

        public int getModelContextMaxApproxTokens() {
            return modelContextMaxApproxTokens == null
                    ? DEFAULT_MODEL_CONTEXT_MAX_APPROX_TOKENS
                    : modelContextMaxApproxTokens;
        }

        public String getModelContextTokenCountMode() {
            return normalizeModelContextTokenCountMode(modelContextTokenCountMode);
        }

        String getModelContextTokenCountModeRaw() {
            return modelContextTokenCountMode;
        }

        public int getReactMaxIterations() {
            return reactMaxIterations == null
                    ? DEFAULT_REACT_MAX_ITERATIONS
                    : reactMaxIterations;
        }

        public int getReactMaxExecutionSeconds() {
            return reactMaxExecutionSeconds == null
                    ? DEFAULT_REACT_MAX_EXECUTION_SECONDS
                    : reactMaxExecutionSeconds;
        }

        public int getReactRepeatedToolCallThreshold() {
            return reactRepeatedToolCallThreshold == null
                    ? DEFAULT_REACT_REPEATED_TOOL_CALL_THRESHOLD
                    : reactRepeatedToolCallThreshold;
        }

        public int getTaskStatePlanMaxChars() {
            return taskStatePlanMaxChars == null
                    ? DEFAULT_TASK_STATE_PLAN_MAX_CHARS
                    : taskStatePlanMaxChars;
        }

        public int getTaskStateInProgressMaxChars() {
            return taskStateInProgressMaxChars == null
                    ? DEFAULT_TASK_STATE_IN_PROGRESS_MAX_CHARS
                    : taskStateInProgressMaxChars;
        }

        public int getTaskStateLastFailureMaxChars() {
            return taskStateLastFailureMaxChars == null
                    ? DEFAULT_TASK_STATE_LAST_FAILURE_MAX_CHARS
                    : taskStateLastFailureMaxChars;
        }

        public int getTaskStateTodoMaxItems() {
            return taskStateTodoMaxItems == null
                    ? DEFAULT_TASK_STATE_TODO_MAX_ITEMS
                    : taskStateTodoMaxItems;
        }

        public int getTaskStateTodoItemMaxChars() {
            return taskStateTodoItemMaxChars == null
                    ? DEFAULT_TASK_STATE_TODO_ITEM_MAX_CHARS
                    : taskStateTodoItemMaxChars;
        }

        public boolean isToolResultBudgetEnabled() {
            return toolResultBudgetEnabled == null
                    ? DEFAULT_TOOL_RESULT_BUDGET_ENABLED
                    : Boolean.TRUE.equals(toolResultBudgetEnabled);
        }

        public int getToolResultBudgetMinChars() {
            return toolResultBudgetMinChars == null
                    ? DEFAULT_TOOL_RESULT_BUDGET_MIN_CHARS
                    : toolResultBudgetMinChars;
        }

        public int getToolResultBudgetPreviewHeadChars() {
            return toolResultBudgetPreviewHeadChars == null
                    ? DEFAULT_TOOL_RESULT_BUDGET_PREVIEW_HEAD_CHARS
                    : toolResultBudgetPreviewHeadChars;
        }

        public int getToolResultBudgetPreviewTailChars() {
            return toolResultBudgetPreviewTailChars == null
                    ? DEFAULT_TOOL_RESULT_BUDGET_PREVIEW_TAIL_CHARS
                    : toolResultBudgetPreviewTailChars;
        }

        public int getToolResultBudgetDecayChars() {
            return toolResultBudgetDecayChars == null
                    ? DEFAULT_TOOL_RESULT_BUDGET_DECAY_CHARS
                    : toolResultBudgetDecayChars;
        }

        public boolean isShellToolEnabled() {
            return shellToolEnabled == null ? DEFAULT_SHELL_TOOL_ENABLED : Boolean.TRUE.equals(shellToolEnabled);
        }

        public int getShellToolTimeoutSeconds() {
            return shellToolTimeoutSeconds == null ? DEFAULT_SHELL_TOOL_TIMEOUT_SECONDS : shellToolTimeoutSeconds;
        }

        /**
         * Negative values are treated as unlimited (0) to avoid invalid explicit config leaking into runtime.
         */
        public void setModelContextMaxMessages(Integer modelContextMaxMessages) {
            this.modelContextMaxMessages = clampNonNegativeOrNull(modelContextMaxMessages);
        }

        /**
         * Negative values are treated as unlimited (0) to avoid invalid explicit config leaking into runtime.
         */
        public void setModelContextMaxTotalMessages(Integer modelContextMaxTotalMessages) {
            this.modelContextMaxTotalMessages = clampNonNegativeOrNull(modelContextMaxTotalMessages);
        }

        /**
         * Negative values are treated as unlimited (0).
         */
        public void setModelContextMaxApproxTokens(Integer modelContextMaxApproxTokens) {
            this.modelContextMaxApproxTokens = clampNonNegativeOrNull(modelContextMaxApproxTokens);
        }

        public void setModelContextTokenCountMode(String modelContextTokenCountMode) {
            this.modelContextTokenCountMode = modelContextTokenCountMode == null
                    ? ""
                    : modelContextTokenCountMode.trim();
        }

        public void setReactMaxIterations(Integer reactMaxIterations) {
            this.reactMaxIterations = clampNonNegativeOrNull(reactMaxIterations);
        }

        public void setReactMaxExecutionSeconds(Integer reactMaxExecutionSeconds) {
            this.reactMaxExecutionSeconds = clampNonNegativeOrNull(reactMaxExecutionSeconds);
        }

        public void setReactRepeatedToolCallThreshold(Integer reactRepeatedToolCallThreshold) {
            this.reactRepeatedToolCallThreshold = clampNonNegativeOrNull(reactRepeatedToolCallThreshold);
        }

        public void setTaskStatePlanMaxChars(Integer taskStatePlanMaxChars) {
            this.taskStatePlanMaxChars = clampPositiveOrNull(taskStatePlanMaxChars);
        }

        public void setTaskStateInProgressMaxChars(Integer taskStateInProgressMaxChars) {
            this.taskStateInProgressMaxChars = clampPositiveOrNull(taskStateInProgressMaxChars);
        }

        public void setTaskStateLastFailureMaxChars(Integer taskStateLastFailureMaxChars) {
            this.taskStateLastFailureMaxChars = clampPositiveOrNull(taskStateLastFailureMaxChars);
        }

        public void setTaskStateTodoMaxItems(Integer taskStateTodoMaxItems) {
            this.taskStateTodoMaxItems = clampPositiveOrNull(taskStateTodoMaxItems);
        }

        public void setTaskStateTodoItemMaxChars(Integer taskStateTodoItemMaxChars) {
            this.taskStateTodoItemMaxChars = clampPositiveOrNull(taskStateTodoItemMaxChars);
        }

        public void setToolResultBudgetMinChars(Integer toolResultBudgetMinChars) {
            this.toolResultBudgetMinChars = clampPositiveOrNull(toolResultBudgetMinChars);
        }

        public void setToolResultBudgetPreviewHeadChars(Integer toolResultBudgetPreviewHeadChars) {
            this.toolResultBudgetPreviewHeadChars = clampPositiveOrNull(toolResultBudgetPreviewHeadChars);
        }

        public void setToolResultBudgetPreviewTailChars(Integer toolResultBudgetPreviewTailChars) {
            this.toolResultBudgetPreviewTailChars = clampPositiveOrNull(toolResultBudgetPreviewTailChars);
        }

        public void setToolResultBudgetDecayChars(Integer toolResultBudgetDecayChars) {
            this.toolResultBudgetDecayChars = clampNonNegativeOrNull(toolResultBudgetDecayChars);
        }

        private static Integer clampPositiveOrNull(Integer value) {
            if (value == null) {
                return null;
            }
            return value <= 0 ? null : value;
        }

        private static Integer clampNonNegativeOrNull(Integer value) {
            if (value == null) {
                return null;
            }
            return value < 0 ? 0 : value;
        }

        private static String normalizeModelContextTokenCountMode(String value) {
            if (value == null || value.isBlank()) {
                return DEFAULT_MODEL_CONTEXT_TOKEN_COUNT_MODE;
            }
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if ("tokenizer".equals(normalized)) {
                return normalized;
            }
            return DEFAULT_MODEL_CONTEXT_TOKEN_COUNT_MODE;
        }
    }

    /**
     * MCP runtime configuration.
     */
    @Data
    public static class McpConfig {
        private static final boolean DEFAULT_ENABLED = false;

        /**
         * Whether MCP tools are allowed to enter the main agent toolchain.
         * Disabled by default in the current stage.
         */
        private Boolean enabled = null;

        public boolean isEnabled() {
            return enabled == null ? DEFAULT_ENABLED : Boolean.TRUE.equals(enabled);
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled == null ? null : Boolean.TRUE.equals(enabled);
        }
    }

    /**
     * Legacy mapping warn/log behavior configuration.
     */
    @Data
    public static class LegacyMappingConfig {
        private static final int DEFAULT_WARN_SAMPLE_RATE = 200;

        /**
         * Enable sampled warn logs for legacy session-id mapping.
         */
        private Boolean warnEnabled = null;

        /**
         * Sample rate for warn logging. Values <=0 are normalized to default.
         */
        private Integer warnSampleRate = null;

        public boolean isWarnEnabled() {
            return Boolean.TRUE.equals(warnEnabled);
        }

        public int getWarnSampleRate() {
            if (warnSampleRate == null || warnSampleRate <= 0) {
                return DEFAULT_WARN_SAMPLE_RATE;
            }
            return warnSampleRate;
        }

        public void setWarnSampleRate(Integer warnSampleRate) {
            if (warnSampleRate == null) {
                this.warnSampleRate = null;
                return;
            }
            this.warnSampleRate = warnSampleRate <= 0 ? DEFAULT_WARN_SAMPLE_RATE : warnSampleRate;
        }
    }
}
