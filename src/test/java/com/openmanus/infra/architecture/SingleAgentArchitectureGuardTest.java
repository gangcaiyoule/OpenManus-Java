package com.openmanus.infra.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleAgentArchitectureGuardTest {

    private static final Pattern DEPRECATED_ADD_LISTENER_USAGE =
            Pattern.compile("\\.addListener\\s*\\(\\s*[^,\\)]+\\s*\\)");
    private static final Pattern DEPRECATED_REMOVE_LISTENER_USAGE =
            Pattern.compile("\\.removeListener\\s*\\(\\s*[^,\\)]+\\s*\\)");

    @Test
    void shouldNotContainLegacyMultiAgentClasses() {
        String[] forbiddenClasses = {
                "com.openmanus.agent.base.AgentHandoff",
                "com.openmanus.agent.impl.thinker.ThinkingAgent",
                "com.openmanus.agent.impl.executor.SearchAgent",
                "com.openmanus.agent.impl.executor.CodeAgent",
                "com.openmanus.agent.impl.executor.FileAgent",
                "com.openmanus.agent.impl.reflection.ReflectionAgent",
                "com.openmanus.agent.workflow.FastThinkWorkflow",
                "com.openmanus.agent.workflow.ThinkDoReflectWorkflow",
                "com.openmanus.domain.service.ThinkDoReflectService",
                "com.openmanus.infra.config.SubAgentConfig"
        };

        for (String className : forbiddenClasses) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(className),
                    "Forbidden legacy class exists: " + className);
        }
    }

    @Test
    void shouldNotUseDeprecatedGlobalListenerApisOutsideTracker() throws IOException {
        Path mainJavaRoot = Paths.get("src/main/java");
        Path trackerPath = Paths.get("src/main/java/com/openmanus/infra/monitoring/AgentExecutionTracker.java");

        try (Stream<Path> stream = Files.walk(mainJavaRoot)) {
            List<String> violations = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.normalize().equals(trackerPath.normalize()))
                    .flatMap(path -> scanDeprecatedListenerApiUsage(path).stream())
                    .collect(Collectors.toList());

            assertTrue(violations.isEmpty(),
                    "Deprecated global listener API usage found outside AgentExecutionTracker: " + violations);
        }
    }

    private List<String> scanDeprecatedListenerApiUsage(Path javaFile) {
        try {
            List<String> lines = Files.readAllLines(javaFile);
            return lines.stream()
                    .map(String::trim)
                    .filter(line -> DEPRECATED_ADD_LISTENER_USAGE.matcher(line).find()
                            || DEPRECATED_REMOVE_LISTENER_USAGE.matcher(line).find())
                    .map(line -> javaFile + " -> " + line)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + javaFile, e);
        }
    }
}
