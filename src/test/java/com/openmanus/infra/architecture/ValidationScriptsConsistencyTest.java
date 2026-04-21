package com.openmanus.infra.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationScriptsConsistencyTest {
    private static final long PROCESS_TIMEOUT_SECONDS = 30;
    private static final Pattern RETRY_TAG_PATTERN =
            Pattern.compile("(?m)^\\s*readonly\\s+(RETRY_[A-Z_]+_TAG)=\"([^\"]+)\"\\s*$");

    private static final Path VALIDATE_SCRIPT = Path.of("scripts/validate-single-agent.sh");
    private static final Path MVNW_LOCAL_SCRIPT = Path.of("scripts/mvnw-local.sh");
    private static final Path VALIDATION_DOC = Path.of("docs/SINGLE_AGENT_VALIDATION.md");
    private static final Path ROLLOUT_DOC = Path.of("docs/SINGLE_AGENT_ROLLOUT.md");
    private static final Set<String> EXPECTED_RETRY_TAG_KEYS = Set.of(
            "RETRY_SIGNATURE_MATCHED_TAG",
            "RETRY_CLEANUP_TAG",
            "RETRY_STARTED_TAG",
            "RETRY_COMPLETED_TAG"
    );

    @TempDir
    Path tempDir;

    @Test
    void shouldUseMvnwLocalAsSingleMavenEntrypointInValidateScript() throws IOException {
        String content = normalizeShellContinuations(Files.readString(VALIDATE_SCRIPT));
        Map<String, String> retryTags = extractRetryTags(content);
        String retrySignatureMatchedTag = requireRetryTag(retryTags, "RETRY_SIGNATURE_MATCHED_TAG");
        String retryCleanupTag = requireRetryTag(retryTags, "RETRY_CLEANUP_TAG");
        String retryStartedTag = requireRetryTag(retryTags, "RETRY_STARTED_TAG");
        String retryCompletedTag = requireRetryTag(retryTags, "RETRY_COMPLETED_TAG");

        assertTrue(Pattern.compile("(?m)^\\s*\\./scripts/mvnw-local\\.sh\\b").matcher(content).find(),
                "validate-single-agent.sh must invoke mvnw-local.sh");
        assertTrue(Pattern.compile("(?m)^\\s*\\./scripts/mvnw-local\\.sh\\b.*(?:^|\\s)-DskipTests(?:\\s|$).*\\bcompile\\b")
                        .matcher(content)
                        .find(),
                "validate-single-agent.sh must compile via mvnw-local.sh");
        boolean hasInlineTestInvocation = Pattern
                .compile("(?m)^\\s*\\./scripts/mvnw-local\\.sh\\b.*(?:^|\\s)-Dtest=[^\\s]+(?:\\s|$).*\\btest\\b")
                .matcher(content)
                .find();
        boolean hasVariableBasedTestInvocation = content.contains("TEST_ARGS=")
                && content.contains("-Dtest=")
                && (content.contains("./scripts/mvnw-local.sh ${TEST_ARGS}")
                || content.contains("./scripts/mvnw-local.sh \"${TEST_ARGS[@]}\""));
        assertTrue(hasInlineTestInvocation || hasVariableBasedTestInvocation,
                "validate-single-agent.sh must run tests via mvnw-local.sh");
        assertTrue(content.contains("TEST_ARGS=("),
                "validate-single-agent.sh should keep TEST_ARGS as array for safe shell argument handling");
        assertTrue(content.contains("\"${TEST_ARGS[@]}\""),
                "validate-single-agent.sh should invoke mvnw-local.sh with quoted TEST_ARGS array expansion");
        assertTrue(content.contains("MvnwLocalScriptIntegrationTest"),
                "validate-single-agent.sh must keep MvnwLocalScriptIntegrationTest in regression gate");
        assertTrue(content.contains("Unable to access jarfile .*/surefirebooter-.*\\.jar"),
                "validate-single-agent.sh must retry only for transient surefire bootstrap error signature");
        assertTrue(content.contains(retrySignatureMatchedTag),
                "validate-single-agent.sh should emit stable retry signature tag");
        assertTrue(content.contains(retryCleanupTag),
                "validate-single-agent.sh should emit stable retry cleanup tag");
        assertTrue(content.contains(retryStartedTag),
                "validate-single-agent.sh should emit stable retry start tag");
        assertTrue(content.contains(retryCompletedTag),
                "validate-single-agent.sh should emit stable retry completion tag");
        assertTrue(content.contains("rm -rf target/surefire"),
                "validate-single-agent.sh must clear target/surefire before one-time retry");
        assertFalse(Pattern.compile("(?m)^\\s*rm\\s+-rf\\s+.*surefire-reports\\b")
                        .matcher(content)
                        .find(),
                "validate-single-agent.sh must not delete surefire-reports before retry");
        assertTrue(content.contains("mvnw-local.sh is not executable"),
                "validate-single-agent.sh must fail fast when mvnw-local.sh is not executable");
    }

    @Test
    void shouldKeepSafeJavaHomeInferenceRulesInMvnwLocal() throws IOException {
        String content = Files.readString(MVNW_LOCAL_SCRIPT);

        assertTrue(content.contains("JAVAC_REAL_PATH=\"\""),
                "mvnw-local.sh must not trust unresolved javac path");
        assertTrue(content.contains("[[ -n \"$JAVAC_REAL_PATH\" && \"$JAVAC_REAL_PATH\" == */bin/javac ]]"),
                "mvnw-local.sh must only infer JAVA_HOME from canonical javac path");
        assertTrue(content.contains("$CANDIDATE_JAVA_HOME\" != \"/usr\""),
                "mvnw-local.sh must reject /usr as JAVA_HOME candidate");
    }

    @Test
    void shouldKeepValidationDocAlignedWithRegressionGate() throws IOException {
        String content = Files.readString(VALIDATION_DOC);
        String lower = content.toLowerCase();
        Map<String, String> retryTags = extractRetryTags(Files.readString(VALIDATE_SCRIPT));
        String retrySignatureMatchedTag = requireRetryTag(retryTags, "RETRY_SIGNATURE_MATCHED_TAG");
        String retryCleanupTag = requireRetryTag(retryTags, "RETRY_CLEANUP_TAG");
        String retryStartedTag = requireRetryTag(retryTags, "RETRY_STARTED_TAG");
        String retryCompletedTag = requireRetryTag(retryTags, "RETRY_COMPLETED_TAG");

        assertTrue(content.contains("MvnwLocalScriptIntegrationTest"),
                "SINGLE_AGENT_VALIDATION.md must include MvnwLocalScriptIntegrationTest in regression command");
        assertTrue(content.contains("./scripts/mvnw-local.sh"),
                "SINGLE_AGENT_VALIDATION.md should keep mvnw-local.sh as the regression entrypoint");
        assertTrue(content.contains("-Dtest="),
                "SINGLE_AGENT_VALIDATION.md should document regression tests via -Dtest");
        assertTrue(content.contains("test -x scripts/validate-single-agent.sh -a -x scripts/mvnw-local.sh"),
                "SINGLE_AGENT_VALIDATION.md should keep executable-bit CI pre-check");
        assertTrue(content.contains("target/surefire"),
                "SINGLE_AGENT_VALIDATION.md should document surefire temp cleanup before retry");
        assertTrue(content.contains("target/surefire-reports"),
                "SINGLE_AGENT_VALIDATION.md should state that surefire reports are preserved for diagnostics");
        assertTrue(lower.contains("retry") && lower.contains("diagnostic"),
                "SINGLE_AGENT_VALIDATION.md should mention diagnostics guidance for retry failures");
        assertTrue(content.contains(retrySignatureMatchedTag)
                        && content.contains(retryCleanupTag)
                        && content.contains(retryStartedTag)
                        && content.contains(retryCompletedTag),
                "SINGLE_AGENT_VALIDATION.md should document stable retry CI tags");
    }

    @Test
    void shouldKeepRolloutDocAlignedWithRegressionGate() throws IOException {
        String content = Files.readString(ROLLOUT_DOC);
        String lower = content.toLowerCase();
        Map<String, String> retryTags = extractRetryTags(Files.readString(VALIDATE_SCRIPT));
        String retrySignatureMatchedTag = requireRetryTag(retryTags, "RETRY_SIGNATURE_MATCHED_TAG");
        String retryCleanupTag = requireRetryTag(retryTags, "RETRY_CLEANUP_TAG");
        String retryStartedTag = requireRetryTag(retryTags, "RETRY_STARTED_TAG");
        String retryCompletedTag = requireRetryTag(retryTags, "RETRY_COMPLETED_TAG");

        assertTrue(content.contains("MvnwLocalScriptIntegrationTest"),
                "SINGLE_AGENT_ROLLOUT.md should mention MvnwLocalScriptIntegrationTest in pre-release gate expectations");
        assertTrue(content.contains("./scripts/validate-single-agent.sh"),
                "SINGLE_AGENT_ROLLOUT.md should keep validate-single-agent.sh as pre-release gate entrypoint");
        assertTrue(content.contains("test -x scripts/validate-single-agent.sh -a -x scripts/mvnw-local.sh"),
                "SINGLE_AGENT_ROLLOUT.md should keep executable-bit CI pre-check");
        assertTrue(content.contains("Unable to access jarfile .../surefirebooter-*.jar"),
                "SINGLE_AGENT_ROLLOUT.md should document transient surefire bootstrap one-time retry behavior");
        assertTrue(content.contains("target/surefire"),
                "SINGLE_AGENT_ROLLOUT.md should document surefire temp cleanup before retry");
        assertTrue(content.contains("target/surefire-reports"),
                "SINGLE_AGENT_ROLLOUT.md should state that surefire reports are preserved for diagnostics");
        assertTrue(lower.contains("retry") && lower.contains("diagnostic"),
                "SINGLE_AGENT_ROLLOUT.md should mention diagnostics guidance for retry failures");
        assertTrue(content.contains(retrySignatureMatchedTag)
                        && content.contains(retryCleanupTag)
                        && content.contains(retryStartedTag)
                        && content.contains(retryCompletedTag),
                "SINGLE_AGENT_ROLLOUT.md should document stable retry CI tags");
    }

    @Test
    void shouldRetryOnceAndPreserveSurefireReportsOnTransientBootstrapFailure() throws Exception {
        String bash = requireBash();
        Map<String, String> retryTags = extractRetryTags(Files.readString(VALIDATE_SCRIPT));
        String retrySignatureMatchedTag = requireRetryTag(retryTags, "RETRY_SIGNATURE_MATCHED_TAG");
        String retryCleanupTag = requireRetryTag(retryTags, "RETRY_CLEANUP_TAG");
        String retryStartedTag = requireRetryTag(retryTags, "RETRY_STARTED_TAG");
        String retryCompletedTag = requireRetryTag(retryTags, "RETRY_COMPLETED_TAG");

        Path repo = tempDir.resolve("repo");
        Path scriptsDir = repo.resolve("scripts");
        Path fakeBin = repo.resolve("fake-bin");
        Path targetDir = repo.resolve("target");
        Files.createDirectories(scriptsDir);
        Files.createDirectories(fakeBin);
        Files.createDirectories(repo.resolve("src/main/java"));
        Files.createDirectories(repo.resolve("src/test/java"));
        Files.createDirectories(targetDir.resolve("surefire"));
        Files.createDirectories(targetDir.resolve("surefire-reports"));

        Path validateScript = scriptsDir.resolve("validate-single-agent.sh");
        Files.writeString(validateScript, Files.readString(VALIDATE_SCRIPT), StandardCharsets.UTF_8);
        setExecutable(validateScript);

        Path mvnwLocal = scriptsDir.resolve("mvnw-local.sh");
        Path regressionCallCount = repo.resolve(".regression-call-count");
        String fakeMvnw = "#!/usr/bin/env bash\n"
                + "set -euo pipefail\n"
                + "ARGS=\"$*\"\n"
                + "if [[ \"$ARGS\" == *\"-DskipTests compile\"* ]]; then\n"
                + "  exit 0\n"
                + "fi\n"
                + "if [[ \"$ARGS\" == *\"-Dtest=\"*\" test\"* ]]; then\n"
                + "  COUNT=0\n"
                + "  if [[ -f \"" + regressionCallCount + "\" ]]; then\n"
                + "    COUNT=\"$(cat \"" + regressionCallCount + "\")\"\n"
                + "  fi\n"
                + "  COUNT=$((COUNT + 1))\n"
                + "  echo \"$COUNT\" > \"" + regressionCallCount + "\"\n"
                + "  if [[ \"$COUNT\" -eq 1 ]]; then\n"
                + "    echo \"Error: Unable to access jarfile " + repo + "/target/surefire/surefirebooter-123.jar\" >&2\n"
                + "    exit 1\n"
                + "  fi\n"
                + "  exit 0\n"
                + "fi\n"
                + "exit 0\n";
        Files.writeString(mvnwLocal, fakeMvnw, StandardCharsets.UTF_8);
        setExecutable(mvnwLocal);

        Path rg = fakeBin.resolve("rg");
        Files.writeString(rg, "#!/usr/bin/env bash\nexit 1\n", StandardCharsets.UTF_8);
        setExecutable(rg);

        ProcessBuilder pb = new ProcessBuilder(bash, "./scripts/validate-single-agent.sh")
                .directory(repo.toFile())
                .redirectErrorStream(true);
        Map<String, String> env = pb.environment();
        env.put("PATH", fakeBin + ":/usr/bin:/bin");
        Process process = pb.start();
        boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
        assertTrue(finished, "validate-single-agent.sh behavior test process timed out");
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.exitValue();

        assertEquals(0, exit, output);
        assertTrue(output.contains(retrySignatureMatchedTag),
                "should emit stable retry signature tag");
        assertTrue(output.contains(retryCleanupTag),
                "should emit stable retry cleanup tag");
        assertTrue(output.contains(retryStartedTag), "should emit stable retry start tag");
        assertEquals(1, countOccurrences(output, retrySignatureMatchedTag),
                "stable retry signature tag should appear exactly once");
        assertEquals(1, countOccurrences(output, retryCleanupTag),
                "stable retry cleanup tag should appear exactly once");
        assertEquals(1, countOccurrences(output, retryStartedTag),
                "stable retry start tag should appear exactly once");
        assertTrue(output.contains(retryCompletedTag),
                "should emit stable retry completion tag");
        assertEquals(1, countOccurrences(output, retryCompletedTag),
                "stable retry completion tag should appear exactly once");
        assertTrue(Files.exists(targetDir.resolve("surefire-reports")),
                "surefire reports should be preserved after retry");
        assertFalse(Files.exists(targetDir.resolve("surefire")),
                "surefire temp directory should be cleared before retry");
        assertEquals("2", Files.readString(regressionCallCount).trim(),
                "regression test invocation should run exactly twice (first fail + one retry)");
    }

    private static String normalizeShellContinuations(String script) {
        return script.replaceAll("\\\\\\s*\\R\\s*", " ");
    }

    private String requireBash() {
        Path bashBin = Path.of("/bin/bash");
        if (Files.isExecutable(bashBin)) {
            return bashBin.toString();
        }

        Path bashUsrBin = Path.of("/usr/bin/bash");
        if (Files.isExecutable(bashUsrBin)) {
            return bashUsrBin.toString();
        }

        String discovered = discoverBashFromPath();
        Assumptions.assumeTrue(discovered != null, "bash is required for validate script behavior test");
        return discovered;
    }

    private String discoverBashFromPath() {
        try {
            Process process = new ProcessBuilder("sh", "-lc", "command -v bash")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                return null;
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exit = process.exitValue();
            if (exit != 0 || output.isEmpty()) {
                return null;
            }
            String firstLine = output.lines().findFirst().orElse("").trim();
            if (firstLine.isEmpty()) {
                return null;
            }
            Path bashPath = Path.of(firstLine);
            return Files.isExecutable(bashPath) ? bashPath.toString() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private static void setExecutable(Path path) throws IOException {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
            );
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException ignored) {
            assertTrue(path.toFile().setExecutable(true, true),
                    "failed to set executable permission for " + path);
        }
    }

    private static int countOccurrences(String source, String token) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(token, index)) != -1) {
            count++;
            index += token.length();
        }
        return count;
    }

    private static Map<String, String> extractRetryTags(String scriptContent) {
        Map<String, String> tags = new HashMap<>();
        var matcher = RETRY_TAG_PATTERN.matcher(scriptContent);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2);
            assertTrue(!value.isBlank(), key + " should not be blank");
            assertFalse(tags.containsKey(key), "duplicate retry tag definition found: " + key);
            tags.put(key, value);
        }
        assertEquals(EXPECTED_RETRY_TAG_KEYS, tags.keySet(),
                "validate-single-agent.sh should define exact retry tag keys");
        return tags;
    }

    private static String requireRetryTag(Map<String, String> retryTags, String key) {
        String value = retryTags.get(key);
        assertTrue(value != null && !value.isBlank(), key + " should be defined in validate-single-agent.sh");
        return value;
    }
}
