package com.openmanus.infra.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.util.Locale;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ_WRITE)
class OpenManusPropertiesEnvFallbackTest {

    @Test
    void shouldUseChatMemoryDefaultsWhenUnspecified() {
        OpenManusProperties properties = new OpenManusProperties();
        properties.applyEnvFallbacks();

        assertEquals(30, properties.getChatMemory().getRetentionDays());
        assertTrue(properties.getChatMemory().isQuarantineCorruptedFiles());
        assertEquals(4000, properties.getChatMemory().getToolResultMaxChars());
        assertEquals(300, properties.getChatMemory().getCompactToolResultHeadChars());
        assertEquals(200, properties.getChatMemory().getCompactToolResultTailChars());
        assertEquals(0, properties.getChatMemory().getModelContextMaxTotalMessages());
        assertEquals(128000, properties.getChatMemory().getModelContextMaxApproxTokens());
        assertEquals("approx", properties.getChatMemory().getModelContextTokenCountMode());
        assertFalse(properties.getChatMemory().isCompactToolResultsEnabled());
        assertTrue(properties.getChatMemory().isToolResultOffloadEnabled());
        assertTrue(properties.getChatMemory().isToolResultRehydrateEnabled());
        assertFalse(properties.getMcp().isEnabled());
        assertFalse(properties.getWebProxy().isEnabled());
        assertEquals(List.of(), properties.getWebProxy().getAllowedOrigins());
        assertEquals(240, properties.getChatMemory().getTaskStatePlanMaxChars());
        assertEquals(120, properties.getChatMemory().getTaskStateInProgressMaxChars());
        assertEquals(240, properties.getChatMemory().getTaskStateLastFailureMaxChars());
        assertEquals(6, properties.getChatMemory().getTaskStateTodoMaxItems());
        assertEquals(120, properties.getChatMemory().getTaskStateTodoItemMaxChars());
        assertEquals(240, properties.getChatMemory().getToolResultOffloadHeadChars());
        assertEquals(160, properties.getChatMemory().getToolResultOffloadTailChars());
        assertEquals(2, properties.getChatMemory().getToolResultRehydrateMaxPerRound());
        assertEquals(20000, properties.getChatMemory().getToolResultArtifactMaxIndexEntriesPerMemory());
    }

    @Test
    void shouldApplyMcpEnabledFromSystemProperty() {
        String key = "OPENMANUS_MCP_ENABLED";
        String original = System.getProperty(key);
        try {
            System.setProperty(key, "true");
            OpenManusProperties properties = new OpenManusProperties();

            properties.applyEnvFallbacks();

            assertTrue(properties.getMcp().isEnabled());
        } finally {
            restoreSystemProperty(key, original);
        }
    }

    @Test
    void shouldIgnoreLiveOnlyFallbacksForDefaultLlm() {
        String liveModelKey = "OPENMANUS_LIVE_MODEL";
        String liveBaseUrlKey = "OPENMANUS_LIVE_BASE_URL";
        String liveApiKeyKey = "OPENMANUS_LIVE_API_KEY";
        String originalLiveModel = System.getProperty(liveModelKey);
        String originalLiveBaseUrl = System.getProperty(liveBaseUrlKey);
        String originalLiveApiKey = System.getProperty(liveApiKeyKey);
        try {
            OpenManusProperties baseline = new OpenManusProperties();
            baseline.getLlm().getDefaultLlm().setModel(" ");
            baseline.getLlm().getDefaultLlm().setBaseUrl(" ");
            baseline.getLlm().getDefaultLlm().setApiKey(" ");
            baseline.applyEnvFallbacks();

            System.setProperty(liveModelKey, "live-only-model");
            System.setProperty(liveBaseUrlKey, "https://live-only.example/v1");
            System.setProperty(liveApiKeyKey, "live-only-key");
            OpenManusProperties properties = new OpenManusProperties();
            properties.getLlm().getDefaultLlm().setModel(" ");
            properties.getLlm().getDefaultLlm().setBaseUrl(" ");
            properties.getLlm().getDefaultLlm().setApiKey(" ");

            properties.applyEnvFallbacks();

            assertEquals(baseline.getLlm().getDefaultLlm().getModel(), properties.getLlm().getDefaultLlm().getModel());
            assertEquals(baseline.getLlm().getDefaultLlm().getBaseUrl(),
                    properties.getLlm().getDefaultLlm().getBaseUrl());
            assertEquals(baseline.getLlm().getDefaultLlm().getApiKey(), properties.getLlm().getDefaultLlm().getApiKey());
        } finally {
            restoreSystemProperty(liveModelKey, originalLiveModel);
            restoreSystemProperty(liveBaseUrlKey, originalLiveBaseUrl);
            restoreSystemProperty(liveApiKeyKey, originalLiveApiKey);
        }
    }

    @Test
    void shouldApplyDefaultLlmFallbacksFromOpenAiCompatibilityEnv() {
        String modelKey = "OPENAI_MODEL";
        String baseUrlKey = "OPENAI_BASE_URL";
        String apiKeyKey = "OPENAI_API_KEY";
        String originalModel = System.getProperty(modelKey);
        String originalBaseUrl = System.getProperty(baseUrlKey);
        String originalApiKey = System.getProperty(apiKeyKey);
        try {
            System.setProperty(modelKey, "legacy-openai-model");
            System.setProperty(baseUrlKey, "https://legacy-openai.example/v1");
            System.setProperty(apiKeyKey, "legacy-openai-key");
            OpenManusProperties properties = new OpenManusProperties();
            properties.getLlm().getDefaultLlm().setModel(" ");
            properties.getLlm().getDefaultLlm().setBaseUrl(" ");
            properties.getLlm().getDefaultLlm().setApiKey(" ");

            properties.applyEnvFallbacks();

            assertEquals("legacy-openai-model", properties.getLlm().getDefaultLlm().getModel());
            assertEquals("https://legacy-openai.example/v1", properties.getLlm().getDefaultLlm().getBaseUrl());
            assertEquals("legacy-openai-key", properties.getLlm().getDefaultLlm().getApiKey());
        } finally {
            restoreSystemProperty(modelKey, originalModel);
            restoreSystemProperty(baseUrlKey, originalBaseUrl);
            restoreSystemProperty(apiKeyKey, originalApiKey);
        }
    }

    @Test
    void shouldNotOverrideExplicitDefaultLlmWhenFallbackEnvExists() {
        String modelKey = "OPENAI_MODEL";
        String baseUrlKey = "OPENAI_BASE_URL";
        String apiKeyKey = "OPENAI_API_KEY";
        String originalModel = System.getProperty(modelKey);
        String originalBaseUrl = System.getProperty(baseUrlKey);
        String originalApiKey = System.getProperty(apiKeyKey);
        try {
            System.setProperty(modelKey, "legacy-openai-model");
            System.setProperty(baseUrlKey, "https://legacy-openai.example/v1");
            System.setProperty(apiKeyKey, "legacy-openai-key");
            OpenManusProperties properties = new OpenManusProperties();
            properties.getLlm().getDefaultLlm().setModel("configured-model");
            properties.getLlm().getDefaultLlm().setBaseUrl("https://configured.example/v1");
            properties.getLlm().getDefaultLlm().setApiKey("configured-key");

            properties.applyEnvFallbacks();

            assertEquals("configured-model", properties.getLlm().getDefaultLlm().getModel());
            assertEquals("https://configured.example/v1", properties.getLlm().getDefaultLlm().getBaseUrl());
            assertEquals("configured-key", properties.getLlm().getDefaultLlm().getApiKey());
        } finally {
            restoreSystemProperty(modelKey, originalModel);
            restoreSystemProperty(baseUrlKey, originalBaseUrl);
            restoreSystemProperty(apiKeyKey, originalApiKey);
        }
    }

    @Test
    void shouldApplyWebProxySettingsFromSystemProperty() {
        String enabledKey = "OPENMANUS_WEB_PROXY_ENABLED";
        String originsKey = "OPENMANUS_WEB_PROXY_ALLOWED_ORIGINS";
        String oldEnabled = System.getProperty(enabledKey);
        String oldOrigins = System.getProperty(originsKey);
        try {
            System.setProperty(enabledKey, "true");
            System.setProperty(originsKey, " https://app.example.com , , https://admin.example.com ");
            OpenManusProperties properties = new OpenManusProperties();

            properties.applyEnvFallbacks();

            assertTrue(properties.getWebProxy().isEnabled());
            assertEquals(List.of("https://app.example.com", "https://admin.example.com"),
                    properties.getWebProxy().getAllowedOrigins());
        } finally {
            restoreSystemProperty(enabledKey, oldEnabled);
            restoreSystemProperty(originsKey, oldOrigins);
        }
    }

    @Test
    void shouldNotOverrideExplicitWebProxyOriginsWhenSystemPropertyIsSet() {
        String key = "OPENMANUS_WEB_PROXY_ALLOWED_ORIGINS";
        String original = System.getProperty(key);
        try {
            System.setProperty(key, "https://env.example.com");
            OpenManusProperties properties = new OpenManusProperties();
            properties.getWebProxy().setAllowedOrigins(List.of("https://explicit.example.com"));

            properties.applyEnvFallbacks();

            assertEquals(List.of("https://explicit.example.com"), properties.getWebProxy().getAllowedOrigins());
        } finally {
            restoreSystemProperty(key, original);
        }
    }

    @Test
    void shouldApplyTaskStateBudgetSettingsFromSystemProperty() {
        String planKey = "OPENMANUS_CHAT_MEMORY_TASK_STATE_PLAN_MAX_CHARS";
        String progressKey = "OPENMANUS_CHAT_MEMORY_TASK_STATE_IN_PROGRESS_MAX_CHARS";
        String failureKey = "OPENMANUS_CHAT_MEMORY_TASK_STATE_LAST_FAILURE_MAX_CHARS";
        String todoItemsKey = "OPENMANUS_CHAT_MEMORY_TASK_STATE_TODO_MAX_ITEMS";
        String todoCharsKey = "OPENMANUS_CHAT_MEMORY_TASK_STATE_TODO_ITEM_MAX_CHARS";
        String oldPlan = System.getProperty(planKey);
        String oldProgress = System.getProperty(progressKey);
        String oldFailure = System.getProperty(failureKey);
        String oldTodoItems = System.getProperty(todoItemsKey);
        String oldTodoChars = System.getProperty(todoCharsKey);
        try {
            System.setProperty(planKey, "300");
            System.setProperty(progressKey, "130");
            System.setProperty(failureKey, "280");
            System.setProperty(todoItemsKey, "7");
            System.setProperty(todoCharsKey, "90");
            OpenManusProperties properties = new OpenManusProperties();

            properties.applyEnvFallbacks();

            assertEquals(300, properties.getChatMemory().getTaskStatePlanMaxChars());
            assertEquals(130, properties.getChatMemory().getTaskStateInProgressMaxChars());
            assertEquals(280, properties.getChatMemory().getTaskStateLastFailureMaxChars());
            assertEquals(7, properties.getChatMemory().getTaskStateTodoMaxItems());
            assertEquals(90, properties.getChatMemory().getTaskStateTodoItemMaxChars());
        } finally {
            restoreSystemProperty(planKey, oldPlan);
            restoreSystemProperty(progressKey, oldProgress);
            restoreSystemProperty(failureKey, oldFailure);
            restoreSystemProperty(todoItemsKey, oldTodoItems);
            restoreSystemProperty(todoCharsKey, oldTodoChars);
        }
    }

    @Test
    void shouldNotOverrideExplicitTaskStateBudgetWhenSystemPropertyIsSet() {
        String planKey = "OPENMANUS_CHAT_MEMORY_TASK_STATE_PLAN_MAX_CHARS";
        String original = System.getProperty(planKey);
        try {
            System.setProperty(planKey, "300");
            OpenManusProperties properties = new OpenManusProperties();
            properties.getChatMemory().setTaskStatePlanMaxChars(123);

            properties.applyEnvFallbacks();

            assertEquals(123, properties.getChatMemory().getTaskStatePlanMaxChars());
        } finally {
            restoreSystemProperty(planKey, original);
        }
    }

    @Test
    void shouldFallbackToDefaultTaskStateBudgetsWhenSystemPropertyIsInvalid() {
        String planKey = "OPENMANUS_CHAT_MEMORY_TASK_STATE_PLAN_MAX_CHARS";
        String progressKey = "OPENMANUS_CHAT_MEMORY_TASK_STATE_IN_PROGRESS_MAX_CHARS";
        String failureKey = "OPENMANUS_CHAT_MEMORY_TASK_STATE_LAST_FAILURE_MAX_CHARS";
        String todoItemsKey = "OPENMANUS_CHAT_MEMORY_TASK_STATE_TODO_MAX_ITEMS";
        String todoCharsKey = "OPENMANUS_CHAT_MEMORY_TASK_STATE_TODO_ITEM_MAX_CHARS";
        String oldPlan = System.getProperty(planKey);
        String oldProgress = System.getProperty(progressKey);
        String oldFailure = System.getProperty(failureKey);
        String oldTodoItems = System.getProperty(todoItemsKey);
        String oldTodoChars = System.getProperty(todoCharsKey);
        try {
            System.setProperty(planKey, "abc");
            System.setProperty(progressKey, "abc");
            System.setProperty(failureKey, "abc");
            System.setProperty(todoItemsKey, "abc");
            System.setProperty(todoCharsKey, "abc");
            OpenManusProperties properties = new OpenManusProperties();

            properties.applyEnvFallbacks();

            assertEquals(240, properties.getChatMemory().getTaskStatePlanMaxChars());
            assertEquals(120, properties.getChatMemory().getTaskStateInProgressMaxChars());
            assertEquals(240, properties.getChatMemory().getTaskStateLastFailureMaxChars());
            assertEquals(6, properties.getChatMemory().getTaskStateTodoMaxItems());
            assertEquals(120, properties.getChatMemory().getTaskStateTodoItemMaxChars());
        } finally {
            restoreSystemProperty(planKey, oldPlan);
            restoreSystemProperty(progressKey, oldProgress);
            restoreSystemProperty(failureKey, oldFailure);
            restoreSystemProperty(todoItemsKey, oldTodoItems);
            restoreSystemProperty(todoCharsKey, oldTodoChars);
        }
    }

    @Test
    void shouldApplyArtifactIndexCapFromSystemProperty() {
        String key = "OPENMANUS_CHAT_MEMORY_TOOL_RESULT_ARTIFACT_MAX_INDEX_ENTRIES_PER_MEMORY";
        String original = System.getProperty(key);
        try {
            System.setProperty(key, "1234");
            OpenManusProperties properties = new OpenManusProperties();

            properties.applyEnvFallbacks();

            assertEquals(1234, properties.getChatMemory().getToolResultArtifactMaxIndexEntriesPerMemory());
        } finally {
            restoreSystemProperty(key, original);
        }
    }

    @Test
    void shouldApplyToolResultOffloadPreviewCharsFromSystemProperty() {
        String headKey = "OPENMANUS_CHAT_MEMORY_TOOL_RESULT_OFFLOAD_HEAD_CHARS";
        String tailKey = "OPENMANUS_CHAT_MEMORY_TOOL_RESULT_OFFLOAD_TAIL_CHARS";
        String oldHead = System.getProperty(headKey);
        String oldTail = System.getProperty(tailKey);
        try {
            System.setProperty(headKey, "320");
            System.setProperty(tailKey, "180");
            OpenManusProperties properties = new OpenManusProperties();

            properties.applyEnvFallbacks();

            assertEquals(320, properties.getChatMemory().getToolResultOffloadHeadChars());
            assertEquals(180, properties.getChatMemory().getToolResultOffloadTailChars());
        } finally {
            restoreSystemProperty(headKey, oldHead);
            restoreSystemProperty(tailKey, oldTail);
        }
    }

    @Test
    void shouldApplyChatMemoryDirFromSystemPropertyWhenConfigIsBlank() {
        String key = "OPENMANUS_CHAT_MEMORY_FILE_STORE_DIR";
        String original = System.getProperty(key);
        try {
            System.setProperty(key, "/tmp/openmanus-chat-memory-env");
            OpenManusProperties properties = new OpenManusProperties();
            properties.getChatMemory().setFileStoreDir(" ");

            properties.applyEnvFallbacks();

            assertEquals("/tmp/openmanus-chat-memory-env", properties.getChatMemory().getFileStoreDir());
        } finally {
            restoreSystemProperty(key, original);
        }
    }

    @Test
    void shouldApplyChatMemoryDirFromSystemPropertyWhenConfigUsesDefaultValue() {
        String key = "OPENMANUS_CHAT_MEMORY_FILE_STORE_DIR";
        String original = System.getProperty(key);
        try {
            System.setProperty(key, "/tmp/openmanus-chat-memory-env-default");
            OpenManusProperties properties = new OpenManusProperties();

            properties.applyEnvFallbacks();

            assertEquals("/tmp/openmanus-chat-memory-env-default", properties.getChatMemory().getFileStoreDir());
        } finally {
            restoreSystemProperty(key, original);
        }
    }

    @Test
    void shouldNotOverrideConfiguredChatMemoryDir() {
        String key = "OPENMANUS_CHAT_MEMORY_FILE_STORE_DIR";
        String original = System.getProperty(key);
        try {
            System.setProperty(key, "/tmp/openmanus-chat-memory-env");
            OpenManusProperties properties = new OpenManusProperties();
            properties.getChatMemory().setFileStoreDir("/data/chat-memory");

            properties.applyEnvFallbacks();

            assertEquals("/data/chat-memory", properties.getChatMemory().getFileStoreDir());
        } finally {
            restoreSystemProperty(key, original);
        }
    }

    @Test
    void shouldApplyToolResultArtifactStoreDirFromSystemPropertyWhenConfigUsesDefaultValue() {
        String key = "OPENMANUS_CHAT_MEMORY_TOOL_RESULT_ARTIFACT_STORE_DIR";
        String original = System.getProperty(key);
        try {
            System.setProperty(key, "/tmp/openmanus-artifacts-env-default");
            OpenManusProperties properties = new OpenManusProperties();

            properties.applyEnvFallbacks();

            assertEquals("/tmp/openmanus-artifacts-env-default",
                    properties.getChatMemory().getToolResultArtifactStoreDir());
        } finally {
            restoreSystemProperty(key, original);
        }
    }

    @Test
    void shouldNotOverrideConfiguredToolResultArtifactStoreDir() {
        String key = "OPENMANUS_CHAT_MEMORY_TOOL_RESULT_ARTIFACT_STORE_DIR";
        String original = System.getProperty(key);
        try {
            System.setProperty(key, "/tmp/openmanus-artifacts-env");
            OpenManusProperties properties = new OpenManusProperties();
            properties.getChatMemory().setToolResultArtifactStoreDir("/data/tool-artifacts");

            properties.applyEnvFallbacks();

            assertEquals("/data/tool-artifacts", properties.getChatMemory().getToolResultArtifactStoreDir());
        } finally {
            restoreSystemProperty(key, original);
        }
    }

    @Test
    void shouldApplyRetentionAndQuarantineFromSystemProperty() {
        String retentionKey = "OPENMANUS_CHAT_MEMORY_RETENTION_DAYS";
        String quarantineKey = "OPENMANUS_CHAT_MEMORY_QUARANTINE_CORRUPTED_FILES";
        String oldRetention = System.getProperty(retentionKey);
        String oldQuarantine = System.getProperty(quarantineKey);
        try {
            System.setProperty(retentionKey, "7");
            System.setProperty(quarantineKey, "false");
            OpenManusProperties properties = new OpenManusProperties();

            properties.applyEnvFallbacks();

            assertEquals(7, properties.getChatMemory().getRetentionDays());
            assertFalse(properties.getChatMemory().isQuarantineCorruptedFiles());
        } finally {
            restoreSystemProperty(retentionKey, oldRetention);
            restoreSystemProperty(quarantineKey, oldQuarantine);
        }
    }

    @Test
    void shouldIgnoreInvalidQuarantineBooleanAndKeepDefault() {
        String key = "OPENMANUS_CHAT_MEMORY_QUARANTINE_CORRUPTED_FILES";
        String original = System.getProperty(key);
        try {
            System.setProperty(key, "yes");
            OpenManusProperties properties = new OpenManusProperties();

            properties.applyEnvFallbacks();

            assertTrue(properties.getChatMemory().isQuarantineCorruptedFiles());
        } finally {
            restoreSystemProperty(key, original);
        }
    }

    @Test
    void shouldIgnoreInvalidCompactFlagAndKeepExplicitConfig() {
        String key = "OPENMANUS_CHAT_MEMORY_COMPACT_TOOL_RESULTS_ENABLED";
        String original = System.getProperty(key);
        try {
            System.setProperty(key, "on");
            OpenManusProperties properties = new OpenManusProperties();
            properties.getChatMemory().setCompactToolResultsEnabled(true);

            properties.applyEnvFallbacks();

            assertTrue(properties.getChatMemory().isCompactToolResultsEnabled());
        } finally {
            restoreSystemProperty(key, original);
        }
    }

    @Test
    void shouldApplyValidBooleanFlagsCaseInsensitively() {
        String quarantineKey = "OPENMANUS_CHAT_MEMORY_QUARANTINE_CORRUPTED_FILES";
        String compactKey = "OPENMANUS_CHAT_MEMORY_COMPACT_TOOL_RESULTS_ENABLED";
        String oldQuarantine = System.getProperty(quarantineKey);
        String oldCompact = System.getProperty(compactKey);
        try {
            System.setProperty(quarantineKey, "FALSE");
            System.setProperty(compactKey, "TrUe");
            OpenManusProperties properties = new OpenManusProperties();

            properties.applyEnvFallbacks();

            assertFalse(properties.getChatMemory().isQuarantineCorruptedFiles());
            assertTrue(properties.getChatMemory().isCompactToolResultsEnabled());
        } finally {
            restoreSystemProperty(quarantineKey, oldQuarantine);
            restoreSystemProperty(compactKey, oldCompact);
        }
    }

    @Test
    void shouldNotOverrideConfiguredRetentionAndQuarantine() {
        String retentionKey = "OPENMANUS_CHAT_MEMORY_RETENTION_DAYS";
        String quarantineKey = "OPENMANUS_CHAT_MEMORY_QUARANTINE_CORRUPTED_FILES";
        String oldRetention = System.getProperty(retentionKey);
        String oldQuarantine = System.getProperty(quarantineKey);
        try {
            System.setProperty(retentionKey, "7");
            System.setProperty(quarantineKey, "false");
            OpenManusProperties properties = new OpenManusProperties();
            properties.getChatMemory().setRetentionDays(45);
            properties.getChatMemory().setQuarantineCorruptedFiles(true);

            properties.applyEnvFallbacks();

            assertEquals(45, properties.getChatMemory().getRetentionDays());
            assertTrue(properties.getChatMemory().isQuarantineCorruptedFiles());
        } finally {
            restoreSystemProperty(retentionKey, oldRetention);
            restoreSystemProperty(quarantineKey, oldQuarantine);
        }
    }

    @Test
    void shouldApplyStoreTypeFromSystemProperty() {
        String key = "OPENMANUS_CHAT_MEMORY_STORE_TYPE";
        String original = System.getProperty(key);
        try {
            System.setProperty(key, "in-memory");
            OpenManusProperties properties = new OpenManusProperties();
            properties.getChatMemory().setStoreType(" ");

            properties.applyEnvFallbacks();

            assertEquals("in-memory", properties.getChatMemory().getStoreType());
        } finally {
            restoreSystemProperty(key, original);
        }
    }

    @Test
    void shouldApplyStoreTypeFromSystemPropertyUnderTurkishLocale() {
        String key = "OPENMANUS_CHAT_MEMORY_STORE_TYPE";
        String original = System.getProperty(key);
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            System.setProperty(key, "IN-MEMORY");
            OpenManusProperties properties = new OpenManusProperties();
            properties.getChatMemory().setStoreType(" ");

            properties.applyEnvFallbacks();

            assertEquals("in-memory", properties.getChatMemory().getStoreType());
        } finally {
            Locale.setDefault(originalLocale);
            restoreSystemProperty(key, original);
        }
    }

    @Test
    void shouldApplyToolResultCompactionSettingsFromSystemProperty() {
        String maxCharsKey = "OPENMANUS_CHAT_MEMORY_TOOL_RESULT_MAX_CHARS";
        String headCharsKey = "OPENMANUS_CHAT_MEMORY_COMPACT_HEAD_CHARS";
        String tailCharsKey = "OPENMANUS_CHAT_MEMORY_COMPACT_TAIL_CHARS";
        String compactEnabledKey = "OPENMANUS_CHAT_MEMORY_COMPACT_TOOL_RESULTS_ENABLED";
        String oldMax = System.getProperty(maxCharsKey);
        String oldHead = System.getProperty(headCharsKey);
        String oldTail = System.getProperty(tailCharsKey);
        String oldCompactEnabled = System.getProperty(compactEnabledKey);
        try {
            System.setProperty(maxCharsKey, "2048");
            System.setProperty(headCharsKey, "256");
            System.setProperty(tailCharsKey, "128");
            System.setProperty(compactEnabledKey, "true");
            OpenManusProperties properties = new OpenManusProperties();

            properties.applyEnvFallbacks();

            assertTrue(properties.getChatMemory().isCompactToolResultsEnabled());
            assertEquals(2048, properties.getChatMemory().getToolResultMaxChars());
            assertEquals(256, properties.getChatMemory().getCompactToolResultHeadChars());
            assertEquals(128, properties.getChatMemory().getCompactToolResultTailChars());
        } finally {
            restoreSystemProperty(maxCharsKey, oldMax);
            restoreSystemProperty(headCharsKey, oldHead);
            restoreSystemProperty(tailCharsKey, oldTail);
            restoreSystemProperty(compactEnabledKey, oldCompactEnabled);
        }
    }

    @Test
    void shouldNotOverrideConfiguredToolResultCompactionThresholds() {
        String maxCharsKey = "OPENMANUS_CHAT_MEMORY_TOOL_RESULT_MAX_CHARS";
        String headCharsKey = "OPENMANUS_CHAT_MEMORY_COMPACT_HEAD_CHARS";
        String tailCharsKey = "OPENMANUS_CHAT_MEMORY_COMPACT_TAIL_CHARS";
        String oldMax = System.getProperty(maxCharsKey);
        String oldHead = System.getProperty(headCharsKey);
        String oldTail = System.getProperty(tailCharsKey);
        try {
            System.setProperty(maxCharsKey, "2048");
            System.setProperty(headCharsKey, "256");
            System.setProperty(tailCharsKey, "128");
            OpenManusProperties properties = new OpenManusProperties();
            properties.getChatMemory().setToolResultMaxChars(5000);
            properties.getChatMemory().setCompactToolResultHeadChars(400);
            properties.getChatMemory().setCompactToolResultTailChars(250);

            properties.applyEnvFallbacks();

            assertEquals(5000, properties.getChatMemory().getToolResultMaxChars());
            assertEquals(400, properties.getChatMemory().getCompactToolResultHeadChars());
            assertEquals(250, properties.getChatMemory().getCompactToolResultTailChars());
        } finally {
            restoreSystemProperty(maxCharsKey, oldMax);
            restoreSystemProperty(headCharsKey, oldHead);
            restoreSystemProperty(tailCharsKey, oldTail);
        }
    }

    @Test
    void shouldApplyModelContextMaxMessagesFromSystemProperty() {
        String key = "OPENMANUS_CHAT_MEMORY_MODEL_CONTEXT_MAX_MESSAGES";
        String original = System.getProperty(key);
        try {
            System.setProperty(key, "42");
            OpenManusProperties properties = new OpenManusProperties();

            properties.applyEnvFallbacks();

            assertEquals(42, properties.getChatMemory().getModelContextMaxMessages());
        } finally {
            restoreSystemProperty(key, original);
        }
    }

    @Test
    void shouldApplyModelContextMaxTotalMessagesFromSystemProperty() {
        String key = "OPENMANUS_CHAT_MEMORY_MODEL_CONTEXT_MAX_TOTAL_MESSAGES";
        String original = System.getProperty(key);
        try {
            System.setProperty(key, "24");
            OpenManusProperties properties = new OpenManusProperties();

            properties.applyEnvFallbacks();

            assertEquals(24, properties.getChatMemory().getModelContextMaxTotalMessages());
        } finally {
            restoreSystemProperty(key, original);
        }
    }

    @Test
    void shouldApplyModelContextTokenCountModeFromSystemProperty() {
        String key = "OPENMANUS_CHAT_MEMORY_MODEL_CONTEXT_TOKEN_COUNT_MODE";
        String original = System.getProperty(key);
        try {
            System.setProperty(key, "tokenizer");
            OpenManusProperties properties = new OpenManusProperties();

            properties.applyEnvFallbacks();

            assertEquals("tokenizer", properties.getChatMemory().getModelContextTokenCountMode());
        } finally {
            restoreSystemProperty(key, original);
        }
    }

    @Test
    void shouldFallbackToApproxWhenModelContextTokenCountModeIsInvalid() {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setModelContextTokenCountMode("invalid-mode");

        assertEquals("approx", properties.getChatMemory().getModelContextTokenCountMode());
    }

    @Test
    void shouldNotOverrideConfiguredModelContextTokenCountMode() {
        String key = "OPENMANUS_CHAT_MEMORY_MODEL_CONTEXT_TOKEN_COUNT_MODE";
        String original = System.getProperty(key);
        try {
            System.setProperty(key, "approx");
            OpenManusProperties properties = new OpenManusProperties();
            properties.getChatMemory().setModelContextTokenCountMode("tokenizer");

            properties.applyEnvFallbacks();

            assertEquals("tokenizer", properties.getChatMemory().getModelContextTokenCountMode());
        } finally {
            restoreSystemProperty(key, original);
        }
    }

    @Test
    void shouldNotOverrideConfiguredModelContextMaxMessages() {
        String key = "OPENMANUS_CHAT_MEMORY_MODEL_CONTEXT_MAX_MESSAGES";
        String original = System.getProperty(key);
        try {
            System.setProperty(key, "42");
            OpenManusProperties properties = new OpenManusProperties();
            properties.getChatMemory().setModelContextMaxMessages(12);

            properties.applyEnvFallbacks();

            assertEquals(12, properties.getChatMemory().getModelContextMaxMessages());
        } finally {
            restoreSystemProperty(key, original);
        }
    }

    @Test
    void shouldNotOverrideConfiguredModelContextMaxTotalMessages() {
        String key = "OPENMANUS_CHAT_MEMORY_MODEL_CONTEXT_MAX_TOTAL_MESSAGES";
        String original = System.getProperty(key);
        try {
            System.setProperty(key, "42");
            OpenManusProperties properties = new OpenManusProperties();
            properties.getChatMemory().setModelContextMaxTotalMessages(10);

            properties.applyEnvFallbacks();

            assertEquals(10, properties.getChatMemory().getModelContextMaxTotalMessages());
        } finally {
            restoreSystemProperty(key, original);
        }
    }

    @Test
    void shouldFallbackToDefaultsWhenModelContextLimitsAreInvalid() {
        String historyKey = "OPENMANUS_CHAT_MEMORY_MODEL_CONTEXT_MAX_MESSAGES";
        String totalKey = "OPENMANUS_CHAT_MEMORY_MODEL_CONTEXT_MAX_TOTAL_MESSAGES";
        String oldHistory = System.getProperty(historyKey);
        String oldTotal = System.getProperty(totalKey);
        try {
            System.setProperty(historyKey, "-1");
            System.setProperty(totalKey, "not-a-number");
            OpenManusProperties properties = new OpenManusProperties();

            properties.applyEnvFallbacks();

            assertEquals(0, properties.getChatMemory().getModelContextMaxMessages());
            assertEquals(0, properties.getChatMemory().getModelContextMaxTotalMessages());
        } finally {
            restoreSystemProperty(historyKey, oldHistory);
            restoreSystemProperty(totalKey, oldTotal);
        }
    }

    @Test
    void shouldClampNegativeExplicitModelContextLimitsToZero() {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setModelContextMaxMessages(-5);
        properties.getChatMemory().setModelContextMaxTotalMessages(-9);

        assertEquals(0, properties.getChatMemory().getModelContextMaxMessages());
        assertEquals(0, properties.getChatMemory().getModelContextMaxTotalMessages());
    }

    @Test
    void shouldClampNegativeConfiguredModelContextLimitsToZero() {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getChatMemory().setModelContextMaxMessages(-2);
        properties.getChatMemory().setModelContextMaxTotalMessages(-9);

        properties.applyEnvFallbacks();

        assertEquals(0, properties.getChatMemory().getModelContextMaxMessages());
        assertEquals(0, properties.getChatMemory().getModelContextMaxTotalMessages());
    }

    @Test
    void shouldNotOverrideConfiguredToolResultCompactionFlag() {
        String key = "OPENMANUS_CHAT_MEMORY_COMPACT_TOOL_RESULTS_ENABLED";
        String original = System.getProperty(key);
        try {
            System.setProperty(key, "true");
            OpenManusProperties properties = new OpenManusProperties();
            properties.getChatMemory().setCompactToolResultsEnabled(false);

            properties.applyEnvFallbacks();

            assertFalse(properties.getChatMemory().isCompactToolResultsEnabled());
        } finally {
            restoreSystemProperty(key, original);
        }
    }

    @Test
    void shouldNotOverrideConfiguredStoreType() {
        String key = "OPENMANUS_CHAT_MEMORY_STORE_TYPE";
        String original = System.getProperty(key);
        try {
            System.setProperty(key, "in-memory");
            OpenManusProperties properties = new OpenManusProperties();
            properties.getChatMemory().setStoreType("file");

            properties.applyEnvFallbacks();

            assertEquals("file", properties.getChatMemory().getStoreType());
        } finally {
            restoreSystemProperty(key, original);
        }
    }

    @Test
    void shouldApplyReactLoopSettingsFromSystemProperties() {
        String iterKey = "OPENMANUS_CHAT_MEMORY_REACT_MAX_ITERATIONS";
        String secKey = "OPENMANUS_CHAT_MEMORY_REACT_MAX_EXECUTION_SECONDS";
        String thresholdKey = "OPENMANUS_CHAT_MEMORY_REACT_REPEATED_TOOL_CALL_THRESHOLD";
        String oldIter = System.getProperty(iterKey);
        String oldSec = System.getProperty(secKey);
        String oldThreshold = System.getProperty(thresholdKey);
        try {
            System.setProperty(iterKey, "0");
            System.setProperty(secKey, "300");
            System.setProperty(thresholdKey, "9");
            OpenManusProperties properties = new OpenManusProperties();

            properties.applyEnvFallbacks();

            assertEquals(0, properties.getChatMemory().getReactMaxIterations());
            assertEquals(300, properties.getChatMemory().getReactMaxExecutionSeconds());
            assertEquals(9, properties.getChatMemory().getReactRepeatedToolCallThreshold());
        } finally {
            restoreSystemProperty(iterKey, oldIter);
            restoreSystemProperty(secKey, oldSec);
            restoreSystemProperty(thresholdKey, oldThreshold);
        }
    }

    @Test
    void shouldApplyToolResultRehydrateMaxPerRoundFromSystemProperties() {
        String key = "OPENMANUS_CHAT_MEMORY_TOOL_RESULT_REHYDRATE_MAX_PER_ROUND";
        String old = System.getProperty(key);
        try {
            System.setProperty(key, "5");
            OpenManusProperties properties = new OpenManusProperties();

            properties.applyEnvFallbacks();

            assertEquals(5, properties.getChatMemory().getToolResultRehydrateMaxPerRound());
        } finally {
            restoreSystemProperty(key, old);
        }
    }

    @Test
    void shouldFallbackToDefaultsWhenReactLoopSettingsAreInvalid() {
        String iterKey = "OPENMANUS_CHAT_MEMORY_REACT_MAX_ITERATIONS";
        String secKey = "OPENMANUS_CHAT_MEMORY_REACT_MAX_EXECUTION_SECONDS";
        String thresholdKey = "OPENMANUS_CHAT_MEMORY_REACT_REPEATED_TOOL_CALL_THRESHOLD";
        String oldIter = System.getProperty(iterKey);
        String oldSec = System.getProperty(secKey);
        String oldThreshold = System.getProperty(thresholdKey);
        try {
            System.setProperty(iterKey, "-3");
            System.setProperty(secKey, "bad");
            System.setProperty(thresholdKey, "-1");
            OpenManusProperties properties = new OpenManusProperties();

            properties.applyEnvFallbacks();

            assertEquals(0, properties.getChatMemory().getReactMaxIterations());
            assertEquals(0, properties.getChatMemory().getReactMaxExecutionSeconds());
            assertEquals(0, properties.getChatMemory().getReactRepeatedToolCallThreshold());
        } finally {
            restoreSystemProperty(iterKey, oldIter);
            restoreSystemProperty(secKey, oldSec);
            restoreSystemProperty(thresholdKey, oldThreshold);
        }
    }

    @Test
    void shouldUseLegacyMappingDefaultsWhenUnspecified() {
        OpenManusProperties properties = new OpenManusProperties();

        properties.applyEnvFallbacks();

        assertFalse(properties.getLegacyMapping().isWarnEnabled());
        assertEquals(200, properties.getLegacyMapping().getWarnSampleRate());
    }

    @Test
    void shouldApplyLegacyMappingSettingsFromSystemProperties() {
        String enabledKey = "OPENMANUS_LEGACY_MAPPING_WARN_ENABLED";
        String rateKey = "OPENMANUS_LEGACY_MAPPING_WARN_SAMPLE_RATE";
        String oldEnabled = System.getProperty(enabledKey);
        String oldRate = System.getProperty(rateKey);
        try {
            System.setProperty(enabledKey, "true");
            System.setProperty(rateKey, "-3");
            OpenManusProperties properties = new OpenManusProperties();

            properties.applyEnvFallbacks();

            assertTrue(properties.getLegacyMapping().isWarnEnabled());
            assertEquals(200, properties.getLegacyMapping().getWarnSampleRate());
        } finally {
            restoreSystemProperty(enabledKey, oldEnabled);
            restoreSystemProperty(rateKey, oldRate);
        }
    }

    private static void restoreSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, value);
    }
}
