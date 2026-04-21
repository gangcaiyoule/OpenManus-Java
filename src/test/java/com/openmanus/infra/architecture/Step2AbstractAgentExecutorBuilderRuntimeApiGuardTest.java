package com.openmanus.infra.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Step2AbstractAgentExecutorBuilderRuntimeApiGuardTest {

    private static final Path TARGET =
            Path.of("src/main/java/com/openmanus/agent/base/AbstractAgentExecutor.java");
    private static final Path INDEXED_REHYDRATE_SELECTOR =
            Path.of("src/main/java/com/openmanus/agent/context/IndexedRehydrateSelector.java");
    private static final Path AGENT_ROOT =
            Path.of("src/main/java/com/openmanus/agent");
    private static final Path AGENT_TEST_ROOT =
            Path.of("src/test/java/com/openmanus/agent");
    private static final Path DOMAIN_ROOT =
            Path.of("src/main/java/com/openmanus/domain");
    private static final Path AIFRAMEWORK_ROOT =
            Path.of("src/main/java/com/openmanus/aiframework");
    private static final List<String> INFRA_LOG_REFERENCE_PREFIXES = List.of("com.openmanus.infra.log.");
    private static final List<String> INFRA_RUNTIME_REFERENCE_PREFIXES = List.of(
            "com.openmanus.infra.config.",
            "com.openmanus.infra.sandbox.",
            "com.openmanus.infra.monitoring.");
    private static final List<String> DOMAIN_REFERENCE_PREFIXES = List.of("com.openmanus.domain.");

    @Test
    void builderShouldKeepRuntimeFirstApis() throws IOException {
        if (!Files.exists(TARGET)) {
            return;
        }
        String source = Files.readString(TARGET);

        assertTrue(source.contains("public B aiChatModel(AiChatModel model)"),
                "Step 2 runtime API must keep aiChatModel(AiChatModel)");
        assertTrue(source.contains("public B aiMemoryProvider(AiMemoryProvider aiMemoryProvider)"),
                "Step 2 runtime API must keep aiMemoryProvider(AiMemoryProvider)");
        assertTrue(source.contains("public B systemMessage(String message)"),
                "Step 2 runtime API must keep systemMessage(String)");
    }

    @Test
    void builderShouldRemoveLegacyCompatibilityApis() throws IOException {
        if (!Files.exists(TARGET)) {
            return;
        }
        String source = Files.readString(TARGET);

        assertFalse(source.contains("public B chatModel(Object model)"),
                "Step 2 should remove chatModel(Object)");
        assertFalse(source.contains("public B chatMemoryProvider(Object chatMemoryProvider)"),
                "Step 2 should remove chatMemoryProvider(Object)");
        assertFalse(source.contains("public B systemMessage(Object message)"),
                "Step 2 should remove systemMessage(Object)");
        assertFalse(source.contains("public B legacyRuntimeAdapter("),
                "Step 2 should remove legacyRuntimeAdapter(...) assembly entry");
    }

    @Test
    void runtimeModelShouldBeRequiredWithoutLegacyFallback() throws IOException {
        if (!Files.exists(TARGET)) {
            return;
        }
        String source = Files.readString(TARGET);

        assertTrue(source.contains("if (builder.aiChatModel == null)"),
                "Step 2 should require aiChatModel in resolveAiChatModel");
        assertTrue(source.contains("aiChatModel must be configured"),
                "Step 2 should expose a clear runtime-only validation message");
        assertFalse(source.contains("Either aiChatModel or chatModel must be configured"),
                "Step 2 should remove legacy fallback validation wording");
    }

    @Test
    void runtimeMemoryShouldUseAiMemoryProviderOnly() throws IOException {
        if (!Files.exists(TARGET)) {
            return;
        }
        String source = Files.readString(TARGET);

        assertTrue(source.contains("if (aiMemoryProvider == null || memoryId == null)"),
                "Step 2 should resolve memory via aiMemoryProvider only");
        assertFalse(source.contains("legacyRuntimeAdapter"),
                "Step 2 should remove legacy memory adapter branch");
        assertFalse(source.contains("PersistentAiMemory"),
                "Agent layer should not depend on infra memory implementation");
        assertFalse(source.contains("com.openmanus.infra.memory.ToolResultArtifactStore"),
                "Agent layer should not depend on infra artifact store implementation");
    }

    @Test
    void indexedRehydrateSelectorShouldNotDependOnInfraArtifactStore() throws IOException {
        if (!Files.exists(INDEXED_REHYDRATE_SELECTOR)) {
            return;
        }
        String source = Files.readString(INDEXED_REHYDRATE_SELECTOR);

        assertFalse(source.contains("com.openmanus.infra.memory.ToolResultArtifactStore"),
                "agent/context should not directly depend on infra ToolResultArtifactStore");
    }

    @Test
    void agentAndDomainShouldNotDependOnInfraLogMarkers() throws IOException {
        List<String> violations = new ArrayList<>();
        collectInfraLogImportViolations(AGENT_ROOT, violations);
        collectInfraLogImportViolations(DOMAIN_ROOT, violations);
        assertTrue(violations.isEmpty(),
                "agent/domain must not import infra.log directly: " + violations);
    }

    @Test
    void agentAndDomainShouldNotDependOnInfraConfigSandboxMonitoring() throws IOException {
        List<String> violations = new ArrayList<>();
        collectInfraRuntimeImportViolations(AGENT_ROOT, violations);
        collectInfraRuntimeImportViolations(DOMAIN_ROOT, violations);
        assertTrue(violations.isEmpty(),
                "agent/domain must not import infra config/sandbox/monitoring directly: " + violations);
    }

    @Test
    void aiframeworkShouldNotDependOnDomainPackage() throws IOException {
        List<String> violations = new ArrayList<>();
        collectDomainImportViolations(AIFRAMEWORK_ROOT, violations);
        assertTrue(violations.isEmpty(),
                "aiframework must not import domain package directly: " + violations);
    }

    @Test
    void agentCodeShouldNotDependOnDomainPackage() throws IOException {
        List<String> violations = new ArrayList<>();
        collectDomainImportViolations(AGENT_ROOT, violations);
        collectDomainImportViolations(AGENT_TEST_ROOT, violations);
        assertTrue(violations.isEmpty(),
                "agent main/test code must not import domain package directly: " + violations);
    }

    private void collectInfraLogImportViolations(Path root, List<String> violations) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> pathStream = Files.walk(root)) {
            pathStream
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> violations.addAll(JavaSourceGuardSupport
                            .findForbiddenQualifiedReferencePrefixes(path, INFRA_LOG_REFERENCE_PREFIXES)));
        }
    }

    private void collectInfraRuntimeImportViolations(Path root, List<String> violations) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> pathStream = Files.walk(root)) {
            pathStream
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> violations.addAll(JavaSourceGuardSupport
                            .findForbiddenQualifiedReferencePrefixes(path, INFRA_RUNTIME_REFERENCE_PREFIXES)));
        }
    }

    private void collectDomainImportViolations(Path root, List<String> violations) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> pathStream = Files.walk(root)) {
            pathStream
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> violations.addAll(JavaSourceGuardSupport
                            .findForbiddenQualifiedReferencePrefixes(path, DOMAIN_REFERENCE_PREFIXES)));
        }
    }
}
