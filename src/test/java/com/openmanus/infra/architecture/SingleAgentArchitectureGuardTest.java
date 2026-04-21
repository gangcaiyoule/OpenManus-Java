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
    private static final List<String> FORBIDDEN_DOMAIN_ANNOTATIONS = List.of(
            "@RestController",
            "@Controller",
            "@Service",
            "@Scheduled",
            "@ConditionalOnProperty",
            "@CrossOrigin",
            "@Autowired",
            "@Qualifier"
    );
    private static final List<String> FORBIDDEN_DOMAIN_IMPORT_PREFIXES = List.of(
            "import com.openmanus.aiframework.runtime.",
            "import com.openmanus.infra.web.",
            "import org.springframework.messaging.simp.SimpMessagingTemplate;",
            "import com.openmanus.aiframework.runtime.AiProxyConfig;",
            "import java.net.HttpURLConnection;",
            "import java.net.Proxy;",
            "import java.net.URL;",
            "import java.net.InetSocketAddress;",
            "import java.io.InputStream;",
            "import java.io.ByteArrayOutputStream;",
            "import java.nio.file.Files;",
            "import java.nio.file.Paths;",
            "import java.security.MessageDigest;",
            "import org.slf4j.Marker;",
            "import org.slf4j.MarkerFactory;",
            "import com.openmanus.aiframework.runtime.AiLogMarkers;",
            "import com.openmanus.infra.log.LogMarkers;",
            "import jakarta.servlet.",
            "import org.springframework.web.bind.annotation.",
            "import org.springframework.stereotype.",
            "import org.springframework.scheduling.annotation.",
            "import org.springframework.boot.autoconfigure.condition.",
            "import org.springframework.beans.factory.annotation."
    );
    private static final List<String> FORBIDDEN_DOMAIN_REFERENCE_PREFIXES = List.of(
            "com.openmanus.agent.workflow.UnifiedWorkflow",
            "com.openmanus.aiframework.runtime.",
            "com.openmanus.infra.web.",
            "org.springframework.messaging.simp.SimpMessagingTemplate",
            "java.net.HttpURLConnection",
            "java.net.Proxy",
            "java.net.URL",
            "java.net.InetSocketAddress",
            "java.io.InputStream",
            "java.io.ByteArrayOutputStream",
            "java.nio.file.Files",
            "java.nio.file.Paths",
            "java.security.MessageDigest",
            "org.slf4j.Marker",
            "org.slf4j.MarkerFactory",
            "com.openmanus.aiframework.runtime.AiLogMarkers",
            "com.openmanus.infra.log.LogMarkers",
            "jakarta.servlet.",
            "org.springframework.web.bind.annotation.",
            "org.springframework.stereotype.",
            "org.springframework.scheduling.annotation.",
            "org.springframework.boot.autoconfigure.condition.",
            "org.springframework.beans.factory.annotation."
    );
    private static final List<String> FORBIDDEN_AGENT_ANNOTATIONS = List.of(
            "@RestController",
            "@Controller",
            "@Service",
            "@Component",
            "@Configuration",
            "@Bean",
            "@Autowired",
            "@Qualifier"
    );
    private static final List<String> FORBIDDEN_AGENT_IMPORT_PREFIXES = List.of(
            "import org.springframework.stereotype.",
            "import org.springframework.context.annotation.",
            "import org.springframework.beans.factory.annotation."
    );

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
    void shouldNotKeepControllersInsideDomainPackage() throws IOException {
        Path domainControllerRoot = Paths.get("src/main/java/com/openmanus/domain/controller");
        if (!Files.exists(domainControllerRoot)) {
            return;
        }

        try (Stream<Path> stream = Files.walk(domainControllerRoot)) {
            List<Path> javaFiles = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            assertTrue(javaFiles.isEmpty(),
                    "Domain package must not contain controllers: " + javaFiles);
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

    @Test
    void shouldNotDependOnUnifiedWorkflowDirectlyInsideDomainServices() throws IOException {
        Path domainServiceRoot = Paths.get("src/main/java/com/openmanus/domain/service");

        try (Stream<Path> stream = Files.walk(domainServiceRoot)) {
            List<String> violations = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("WorkflowExecutionPort.java"))
                    .flatMap(path -> JavaSourceGuardSupport.findForbiddenQualifiedReferencePrefixes(
                            path,
                            List.of("com.openmanus.agent.workflow.UnifiedWorkflow")).stream())
                    .collect(Collectors.toList());

            assertTrue(violations.isEmpty(),
                    "Domain services must depend on WorkflowExecutionPort instead of UnifiedWorkflow: " + violations);
        }
    }

    @Test
    void shouldNotImportRuntimeOrWebSocketPushDetailsInsideDomain() throws IOException {
        Path domainRoot = Paths.get("src/main/java/com/openmanus/domain");

        try (Stream<Path> stream = Files.walk(domainRoot)) {
            List<String> violations = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> Stream.concat(
                            JavaSourceGuardSupport.findForbiddenImportPrefixes(path, FORBIDDEN_DOMAIN_IMPORT_PREFIXES).stream(),
                            JavaSourceGuardSupport.findForbiddenQualifiedReferencePrefixes(
                                    path,
                                    FORBIDDEN_DOMAIN_REFERENCE_PREFIXES).stream()))
                    .distinct()
                    .collect(Collectors.toList());

            assertTrue(violations.isEmpty(),
                    "Domain layer must not import runtime or direct WebSocket push details: " + violations);
        }
    }

    @Test
    void shouldNotUseSpringWebServletOrSchedulingAnnotationsInsideDomain() throws IOException {
        Path domainRoot = Paths.get("src/main/java/com/openmanus/domain");

        try (Stream<Path> stream = Files.walk(domainRoot)) {
            List<String> violations = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> JavaSourceGuardSupport.findTrimmedSanitizedLines(
                            path,
                            line -> FORBIDDEN_DOMAIN_ANNOTATIONS.stream().anyMatch(line::contains)).stream())
                    .collect(Collectors.toList());

            assertTrue(violations.isEmpty(),
                    "Domain layer must stay free of Spring Web/Servlet/Scheduling annotations: " + violations);
        }
    }

    @Test
    void shouldKeepAgentLayerFreeOfSpringWiringAnnotationsAndImports() throws IOException {
        Path agentRoot = Paths.get("src/main/java/com/openmanus/agent");

        try (Stream<Path> stream = Files.walk(agentRoot)) {
            List<String> violations = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> Stream.concat(
                            JavaSourceGuardSupport.findForbiddenImportPrefixes(path, FORBIDDEN_AGENT_IMPORT_PREFIXES).stream(),
                            JavaSourceGuardSupport.findTrimmedSanitizedLines(
                                    path,
                                    line -> FORBIDDEN_AGENT_ANNOTATIONS.stream().anyMatch(line::contains)).stream()))
                    .distinct()
                    .collect(Collectors.toList());

            assertTrue(violations.isEmpty(),
                    "Agent layer must stay free of Spring wiring annotations/imports: " + violations);
        }
    }

    @Test
    void shouldKeepSessionSandboxManagerFreeOfContainerRuntimeDetails() {
        Path sandboxManagerPath = Paths.get("src/main/java/com/openmanus/domain/service/SessionSandboxManager.java");

        List<String> violations = JavaSourceGuardSupport.findTrimmedSanitizedLines(
                sandboxManagerPath,
                line -> line.contains(".getContainerId(")
                        || line.contains(".getVncUrl(")
                        || line.contains("isContainerRunning("));

        assertTrue(violations.isEmpty(),
                "SessionSandboxManager must stay at session orchestration level: " + violations);
    }

    @Test
    void shouldKeepSessionSandboxManagerCommentsAndLogsAtSessionOrchestrationLevel() throws IOException {
        Path sandboxManagerPath = Paths.get("src/main/java/com/openmanus/domain/service/SessionSandboxManager.java");
        String source = Files.readString(sandboxManagerPath);

        List<String> forbiddenMarkers = List.of(
                "沙箱容器",
                "VNC 沙箱",
                "创建 VNC 沙箱失败",
                "清理所有沙箱容器"
        );

        List<String> violations = forbiddenMarkers.stream()
                .filter(source::contains)
                .collect(Collectors.toList());

        assertTrue(violations.isEmpty(),
                "SessionSandboxManager comments/logs must stay at session orchestration level: " + violations);
    }

    @Test
    void shouldKeepSessionSandboxInfoFreeOfContainerIdentifiersAndPorts() {
        Path sandboxInfoPath = Paths.get("src/main/java/com/openmanus/domain/model/SessionSandboxInfo.java");

        List<String> violations = JavaSourceGuardSupport.findTrimmedSanitizedLines(
                sandboxInfoPath,
                line -> line.contains("containerId")
                        || line.contains("mappedPort"));

        assertTrue(violations.isEmpty(),
                "SessionSandboxInfo must stay at session snapshot level: " + violations);
    }

    private List<String> scanDeprecatedListenerApiUsage(Path javaFile) {
        return JavaSourceGuardSupport.findTrimmedSanitizedLines(
                javaFile,
                line -> DEPRECATED_ADD_LISTENER_USAGE.matcher(line).find()
                        || DEPRECATED_REMOVE_LISTENER_USAGE.matcher(line).find());
    }
}
