package com.openmanus.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenManus Configuration Properties
 *
 * Centralized configuration management for OpenManus project
 * Supports configuration from application.yml and environment variables
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
     * Legacy mapping logging configuration.
     */
    @NestedConfigurationProperty
    private LegacyMappingConfig legacyMapping = new LegacyMappingConfig();

    @PostConstruct
    void applyEnvFallbacks() {
        if (llm != null && llm.getDefaultLlm() != null) {
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
        }

        if (search != null) {
            String apiKey = search.getApiKey();
            if (isBlank(apiKey)) {
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

        if (chatMemory != null) {
            if (isBlank(chatMemory.getStoreType())) {
                String storeType = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_STORE_TYPE");
                if (!isBlank(storeType)) {
                    chatMemory.setStoreType(storeType.trim().toLowerCase());
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
            if (chatMemory.toolResultMaxChars == null) {
                String maxToolResultChars = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_TOOL_RESULT_MAX_CHARS");
                if (!isBlank(maxToolResultChars)) {
                    chatMemory.setToolResultMaxChars(parsePositiveInt(maxToolResultChars, chatMemory.getToolResultMaxChars()));
                }
            }
            if (chatMemory.compactToolResultHeadChars == null) {
                String compactHeadChars = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_COMPACT_HEAD_CHARS");
                if (!isBlank(compactHeadChars)) {
                    chatMemory.setCompactToolResultHeadChars(parsePositiveInt(
                            compactHeadChars, chatMemory.getCompactToolResultHeadChars()));
                }
            }
            if (chatMemory.compactToolResultTailChars == null) {
                String compactTailChars = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_COMPACT_TAIL_CHARS");
                if (!isBlank(compactTailChars)) {
                    chatMemory.setCompactToolResultTailChars(parsePositiveInt(
                            compactTailChars, chatMemory.getCompactToolResultTailChars()));
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
            if (chatMemory.compactToolResultsEnabled == null) {
                String compactEnabled = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_COMPACT_TOOL_RESULTS_ENABLED");
                if (!isBlank(compactEnabled)) {
                    chatMemory.setCompactToolResultsEnabled(parseBooleanStrict(
                            compactEnabled, chatMemory.isCompactToolResultsEnabled()));
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
            if (chatMemory.toolResultOffloadEnabled == null) {
                String offloadEnabled = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_TOOL_RESULT_OFFLOAD_ENABLED");
                if (!isBlank(offloadEnabled)) {
                    chatMemory.setToolResultOffloadEnabled(parseBooleanStrict(
                            offloadEnabled, chatMemory.isToolResultOffloadEnabled()));
                }
            }
            if (chatMemory.toolResultOffloadMinChars == null) {
                String offloadMinChars = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_TOOL_RESULT_OFFLOAD_MIN_CHARS");
                if (!isBlank(offloadMinChars)) {
                    chatMemory.setToolResultOffloadMinChars(parsePositiveInt(
                            offloadMinChars, chatMemory.getToolResultOffloadMinChars()));
                }
            }
            if (chatMemory.toolResultOffloadHeadChars == null) {
                String offloadHeadChars = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_TOOL_RESULT_OFFLOAD_HEAD_CHARS");
                if (!isBlank(offloadHeadChars)) {
                    chatMemory.setToolResultOffloadHeadChars(parsePositiveInt(
                            offloadHeadChars, chatMemory.getToolResultOffloadHeadChars()));
                }
            }
            if (chatMemory.toolResultOffloadTailChars == null) {
                String offloadTailChars = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_TOOL_RESULT_OFFLOAD_TAIL_CHARS");
                if (!isBlank(offloadTailChars)) {
                    chatMemory.setToolResultOffloadTailChars(parsePositiveInt(
                            offloadTailChars, chatMemory.getToolResultOffloadTailChars()));
                }
            }
            if (shouldApplyToolResultArtifactStoreDirFallback(chatMemory)) {
                String artifactDir = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_TOOL_RESULT_ARTIFACT_STORE_DIR");
                if (!isBlank(artifactDir)) {
                    chatMemory.setToolResultArtifactStoreDir(artifactDir);
                }
            }
            if (chatMemory.toolResultRehydrateEnabled == null) {
                String rehydrateEnabled = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_TOOL_RESULT_REHYDRATE_ENABLED");
                if (!isBlank(rehydrateEnabled)) {
                    chatMemory.setToolResultRehydrateEnabled(parseBooleanStrict(
                            rehydrateEnabled, chatMemory.isToolResultRehydrateEnabled()));
                }
            }
            if (chatMemory.toolResultRehydrateMaxChars == null) {
                String rehydrateMaxChars = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_TOOL_RESULT_REHYDRATE_MAX_CHARS");
                if (!isBlank(rehydrateMaxChars)) {
                    chatMemory.setToolResultRehydrateMaxChars(parsePositiveInt(
                            rehydrateMaxChars, chatMemory.getToolResultRehydrateMaxChars()));
                }
            }
            if (chatMemory.toolResultRehydrateMaxPerRound == null) {
                String rehydrateMaxPerRound = firstNonBlankEnv("OPENMANUS_CHAT_MEMORY_TOOL_RESULT_REHYDRATE_MAX_PER_ROUND");
                if (!isBlank(rehydrateMaxPerRound)) {
                    chatMemory.setToolResultRehydrateMaxPerRound(parseNonNegativeInt(
                            rehydrateMaxPerRound, chatMemory.getToolResultRehydrateMaxPerRound()));
                }
            }
            if (chatMemory.toolResultArtifactMaxIndexEntriesPerMemory == null) {
                String maxIndexEntries = firstNonBlankEnv(
                        "OPENMANUS_CHAT_MEMORY_TOOL_RESULT_ARTIFACT_MAX_INDEX_ENTRIES_PER_MEMORY");
                if (!isBlank(maxIndexEntries)) {
                    chatMemory.setToolResultArtifactMaxIndexEntriesPerMemory(parsePositiveInt(
                            maxIndexEntries, chatMemory.getToolResultArtifactMaxIndexEntriesPerMemory()));
                }
            }
            if (chatMemory.getModelContextMaxTotalMessages() == 1) {
                log.warn("openmanus.chat-memory.model-context-max-total-messages=1 is an extreme mode; "
                        + "current user continuity is prioritized and system/history may be dropped for that round.");
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

    private static String firstNonBlankEnv(String... names) {
        for (String name : names) {
            String value = System.getenv(name);
            if (!isBlank(value)) {
                return value.trim();
            }
            value = System.getProperty(name);
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

    private static boolean shouldApplyToolResultArtifactStoreDirFallback(ChatMemoryConfig chatMemory) {
        if (chatMemory == null) {
            return false;
        }
        String dir = chatMemory.getToolResultArtifactStoreDir();
        if (isBlank(dir)) {
            return true;
        }
        return ChatMemoryConfig.DEFAULT_TOOL_RESULT_ARTIFACT_STORE_DIR.equals(dir);
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
            private String model = "qwen3-max-preview";
            private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/";
            private String apiType = "openai";
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
        private boolean useSandbox = false;
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
        static final String DEFAULT_TOOL_RESULT_ARTIFACT_STORE_DIR =
                System.getProperty("java.io.tmpdir") + "/openmanus/tool-result-artifacts";
        private static final int DEFAULT_RETENTION_DAYS = 30;
        private static final boolean DEFAULT_QUARANTINE_CORRUPTED_FILES = true;
        private static final int DEFAULT_TOOL_RESULT_MAX_CHARS = 4000;
        private static final int DEFAULT_COMPACT_TOOL_RESULT_HEAD_CHARS = 300;
        private static final int DEFAULT_COMPACT_TOOL_RESULT_TAIL_CHARS = 200;
        private static final int DEFAULT_MODEL_CONTEXT_MAX_MESSAGES = 0;
        private static final int DEFAULT_MODEL_CONTEXT_MAX_TOTAL_MESSAGES = 0;
        private static final int DEFAULT_MODEL_CONTEXT_MAX_APPROX_TOKENS = 128000;
        private static final int DEFAULT_REACT_MAX_ITERATIONS = 0;
        private static final int DEFAULT_REACT_MAX_EXECUTION_SECONDS = 0;
        private static final int DEFAULT_REACT_REPEATED_TOOL_CALL_THRESHOLD = 0;
        private static final boolean DEFAULT_TOOL_RESULT_OFFLOAD_ENABLED = true;
        private static final int DEFAULT_TOOL_RESULT_OFFLOAD_MIN_CHARS = 12000;
        private static final int DEFAULT_TOOL_RESULT_OFFLOAD_HEAD_CHARS = 240;
        private static final int DEFAULT_TOOL_RESULT_OFFLOAD_TAIL_CHARS = 160;
        private static final boolean DEFAULT_TOOL_RESULT_REHYDRATE_ENABLED = true;
        private static final int DEFAULT_TOOL_RESULT_REHYDRATE_MAX_CHARS = 8000;
        private static final int DEFAULT_TOOL_RESULT_REHYDRATE_MAX_PER_ROUND = 2;
        private static final int DEFAULT_TOOL_RESULT_ARTIFACT_MAX_INDEX_ENTRIES_PER_MEMORY = 20000;

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
         * Maximum tool-result chars persisted in long-term chat memory.
         * Only used when compact-tool-results-enabled=true.
         */
        private Integer toolResultMaxChars = null;
        /**
         * Number of leading chars preserved when tool result is compacted.
         */
        private Integer compactToolResultHeadChars = null;
        /**
         * Number of trailing chars preserved when tool result is compacted.
         */
        private Integer compactToolResultTailChars = null;
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
         * Whether to compact large tool results before persisting into long-term chat memory.
         * Default false to keep full message continuity.
         */
        private Boolean compactToolResultsEnabled = null;
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
         * Whether to offload very large tool results into external artifact storage (lossless).
         * Chat memory only keeps a compact index card when enabled.
         */
        private Boolean toolResultOffloadEnabled = null;
        /**
         * Minimum chars to trigger tool-result offloading.
         */
        private Integer toolResultOffloadMinChars = null;
        /**
         * Leading preview chars kept in tool-result offload card.
         */
        private Integer toolResultOffloadHeadChars = null;
        /**
         * Trailing preview chars kept in tool-result offload card.
         */
        private Integer toolResultOffloadTailChars = null;
        /**
         * Artifact store directory used when offloading large tool results.
         */
        private String toolResultArtifactStoreDir = DEFAULT_TOOL_RESULT_ARTIFACT_STORE_DIR;
        /**
         * Whether to rehydrate compacted tool results from artifact store into model input.
         */
        private Boolean toolResultRehydrateEnabled = null;
        /**
         * Max chars allowed for each rehydrated tool result.
         */
        private Integer toolResultRehydrateMaxChars = null;
        /**
         * Max rehydrated tool-result blocks injected per model round.
         * 0 means unlimited.
         */
        private Integer toolResultRehydrateMaxPerRound = null;
        /**
         * Per conversation, maximum index references retained in artifact index file.
         * Exceeded history is pruned from the index tail to avoid long-running sessions becoming slow.
         */
        private Integer toolResultArtifactMaxIndexEntriesPerMemory = null;

        public boolean isCompactToolResultsEnabled() {
            return Boolean.TRUE.equals(compactToolResultsEnabled);
        }

        public int getRetentionDays() {
            return retentionDays == null ? DEFAULT_RETENTION_DAYS : retentionDays;
        }

        public boolean isQuarantineCorruptedFiles() {
            return quarantineCorruptedFiles == null ? DEFAULT_QUARANTINE_CORRUPTED_FILES : quarantineCorruptedFiles;
        }

        public int getToolResultMaxChars() {
            return toolResultMaxChars == null ? DEFAULT_TOOL_RESULT_MAX_CHARS : toolResultMaxChars;
        }

        public int getCompactToolResultHeadChars() {
            return compactToolResultHeadChars == null
                    ? DEFAULT_COMPACT_TOOL_RESULT_HEAD_CHARS
                    : compactToolResultHeadChars;
        }

        public int getCompactToolResultTailChars() {
            return compactToolResultTailChars == null
                    ? DEFAULT_COMPACT_TOOL_RESULT_TAIL_CHARS
                    : compactToolResultTailChars;
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

        public boolean isToolResultOffloadEnabled() {
            return toolResultOffloadEnabled == null
                    ? DEFAULT_TOOL_RESULT_OFFLOAD_ENABLED
                    : Boolean.TRUE.equals(toolResultOffloadEnabled);
        }

        public int getToolResultOffloadMinChars() {
            return toolResultOffloadMinChars == null
                    ? DEFAULT_TOOL_RESULT_OFFLOAD_MIN_CHARS
                    : toolResultOffloadMinChars;
        }

        public int getToolResultOffloadHeadChars() {
            return toolResultOffloadHeadChars == null
                    ? DEFAULT_TOOL_RESULT_OFFLOAD_HEAD_CHARS
                    : toolResultOffloadHeadChars;
        }

        public int getToolResultOffloadTailChars() {
            return toolResultOffloadTailChars == null
                    ? DEFAULT_TOOL_RESULT_OFFLOAD_TAIL_CHARS
                    : toolResultOffloadTailChars;
        }

        public boolean isToolResultRehydrateEnabled() {
            return toolResultRehydrateEnabled == null
                    ? DEFAULT_TOOL_RESULT_REHYDRATE_ENABLED
                    : Boolean.TRUE.equals(toolResultRehydrateEnabled);
        }

        public int getToolResultRehydrateMaxChars() {
            return toolResultRehydrateMaxChars == null
                    ? DEFAULT_TOOL_RESULT_REHYDRATE_MAX_CHARS
                    : toolResultRehydrateMaxChars;
        }

        public int getToolResultRehydrateMaxPerRound() {
            return toolResultRehydrateMaxPerRound == null
                    ? DEFAULT_TOOL_RESULT_REHYDRATE_MAX_PER_ROUND
                    : toolResultRehydrateMaxPerRound;
        }

        public int getToolResultArtifactMaxIndexEntriesPerMemory() {
            return toolResultArtifactMaxIndexEntriesPerMemory == null
                    ? DEFAULT_TOOL_RESULT_ARTIFACT_MAX_INDEX_ENTRIES_PER_MEMORY
                    : toolResultArtifactMaxIndexEntriesPerMemory;
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

        public void setReactMaxIterations(Integer reactMaxIterations) {
            this.reactMaxIterations = clampNonNegativeOrNull(reactMaxIterations);
        }

        public void setReactMaxExecutionSeconds(Integer reactMaxExecutionSeconds) {
            this.reactMaxExecutionSeconds = clampNonNegativeOrNull(reactMaxExecutionSeconds);
        }

        public void setReactRepeatedToolCallThreshold(Integer reactRepeatedToolCallThreshold) {
            this.reactRepeatedToolCallThreshold = clampNonNegativeOrNull(reactRepeatedToolCallThreshold);
        }

        public void setToolResultOffloadMinChars(Integer toolResultOffloadMinChars) {
            this.toolResultOffloadMinChars = clampPositiveOrNull(toolResultOffloadMinChars);
        }

        public void setToolResultOffloadHeadChars(Integer toolResultOffloadHeadChars) {
            this.toolResultOffloadHeadChars = clampPositiveOrNull(toolResultOffloadHeadChars);
        }

        public void setToolResultOffloadTailChars(Integer toolResultOffloadTailChars) {
            this.toolResultOffloadTailChars = clampPositiveOrNull(toolResultOffloadTailChars);
        }

        public void setToolResultRehydrateMaxChars(Integer toolResultRehydrateMaxChars) {
            this.toolResultRehydrateMaxChars = clampPositiveOrNull(toolResultRehydrateMaxChars);
        }

        public void setToolResultRehydrateMaxPerRound(Integer toolResultRehydrateMaxPerRound) {
            this.toolResultRehydrateMaxPerRound = clampNonNegativeOrNull(toolResultRehydrateMaxPerRound);
        }

        public void setToolResultArtifactMaxIndexEntriesPerMemory(Integer toolResultArtifactMaxIndexEntriesPerMemory) {
            this.toolResultArtifactMaxIndexEntriesPerMemory = clampPositiveOrNull(toolResultArtifactMaxIndexEntriesPerMemory);
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
