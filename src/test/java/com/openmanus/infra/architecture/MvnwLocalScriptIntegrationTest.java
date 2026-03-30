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
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MvnwLocalScriptIntegrationTest {

    private static final Path SOURCE_SCRIPT = Path.of("scripts/mvnw-local.sh");

    @TempDir
    Path tempDir;

    @Test
    void shouldPassThroughArgsWhenJavaHomeIsValid() throws Exception {
        String bash = requireBash();

        Path root = tempDir.resolve("repo");
        Path scriptsDir = root.resolve("scripts");
        Path fakeBin = root.resolve("fake-bin");
        Path fakeJdkBin = root.resolve("fake-jdk/bin");
        Files.createDirectories(scriptsDir);
        Files.createDirectories(fakeBin);
        Files.createDirectories(fakeJdkBin);

        Path script = copyScript(root, false);
        makeExecutable(fakeJdkBin.resolve("java"), "#!/usr/bin/env bash\nexit 0\n");
        makeExecutable(fakeJdkBin.resolve("javac"), "#!/usr/bin/env bash\nexit 0\n");

        Path argsFile = root.resolve("mvn.args");
        makeExecutable(fakeBin.resolve("mvn"),
                "#!/usr/bin/env bash\nprintf '%s\\n' \"$*\" > \"" + argsFile + "\"\nexit 0\n");

        ProcessResult result = run(
                root,
                Map.of(
                        "JAVA_HOME", root.resolve("fake-jdk").toString(),
                        "PATH", fakeBin + ":/usr/bin:/bin"
                ),
                bash,
                script.toString(),
                "-q",
                "-DskipTests",
                "compile"
        );

        assertEquals(0, result.exitCode, result.output);
        assertTrue(Files.readString(argsFile).contains("-q -DskipTests compile"),
                "mvn arguments should be passed through by mvnw-local.sh");
    }

    @Test
    void shouldFailWithClearMessageWhenJavaHomeIsInvalidAndNoFallbackAvailable() throws Exception {
        String bash = requireBash();

        Path root = tempDir.resolve("repo-invalid");
        Path fakeBin = root.resolve("fake-bin");
        Files.createDirectories(fakeBin);

        Path script = copyScript(root, true);
        makeExecutable(fakeBin.resolve("javac"), "#!/usr/bin/env bash\nexit 0\n");

        ProcessResult result = run(
                root,
                Map.of(
                        "JAVA_HOME", "/invalid/jdk",
                        "PATH", fakeBin + ":/usr/bin:/bin"
                ),
                bash,
                script.toString(),
                "-q",
                "-DskipTests",
                "compile"
        );

        assertEquals(1, result.exitCode);
        assertTrue(result.output.contains("ERROR: JAVA_HOME is not set correctly."),
                "script should fail with explicit JAVA_HOME guidance");
    }

    @Test
    void shouldNotInferUsrWhenCanonicalResolutionIsUnavailable() throws Exception {
        String bash = requireBash();
        Assumptions.assumeTrue(supportsSymbolicLink(), "symbolic links are required for this test");

        Path root = tempDir.resolve("repo-no-canonical");
        Path fakeBin = root.resolve("fake-bin");
        Files.createDirectories(fakeBin);

        Path script = copyScript(root, true);
        Files.createSymbolicLink(fakeBin.resolve("javac"), Path.of("/usr/bin/javac"));
        makeExecutable(fakeBin.resolve("readlink"), "#!/usr/bin/env bash\nexit 1\n");
        makeExecutable(fakeBin.resolve("realpath"), "#!/usr/bin/env bash\nexit 1\n");

        ProcessResult result = run(
                root,
                Map.of("PATH", fakeBin + ":/usr/bin:/bin"),
                bash,
                script.toString(),
                "-q",
                "-DskipTests",
                "compile"
        );

        assertEquals(1, result.exitCode);
        assertTrue(result.output.contains("ERROR: JAVA_HOME is not set correctly."),
                "script must not fall through with a wrong inferred JAVA_HOME");
        assertTrue(result.output.contains("canonical-path resolution"),
                "script should explain why javac fallback did not infer JAVA_HOME");
    }

    private Path copyScript(Path root, boolean disableMacJavaHomeProbe) throws IOException {
        Path scriptsDir = root.resolve("scripts");
        Files.createDirectories(scriptsDir);
        Path script = scriptsDir.resolve("mvnw-local.sh");
        String content = Files.readString(SOURCE_SCRIPT);
        if (disableMacJavaHomeProbe) {
            content = content.replace("/usr/libexec/java_home", "/nonexistent/java_home");
        }
        Files.writeString(script, content, StandardCharsets.UTF_8);
        setExecutable(script);
        return script;
    }

    private void makeExecutable(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
        setExecutable(path);
    }

    private ProcessResult run(Path workDir, Map<String, String> env, String... command)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workDir.toFile())
                .redirectErrorStream(true);
        Map<String, String> processEnv = builder.environment();
        processEnv.remove("JAVA_HOME");
        processEnv.putAll(env);

        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        return new ProcessResult(exit, output);
    }

    private record ProcessResult(int exitCode, String output) {
    }

    private void setExecutable(Path path) throws IOException {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
            );
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException ignored) {
            boolean ok = path.toFile().setExecutable(true, true);
            assertTrue(ok, "failed to set executable bit for " + path);
        }
    }

    private boolean supportsSymbolicLink() {
        Path probe = tempDir.resolve("symlink-probe");
        Path target = tempDir.resolve("symlink-target");
        try {
            Files.writeString(target, "x", StandardCharsets.UTF_8);
            Files.createSymbolicLink(probe, target);
            return true;
        } catch (UnsupportedOperationException | IOException e) {
            return false;
        } finally {
            try {
                Files.deleteIfExists(probe);
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
            }
        }
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
        Assumptions.assumeTrue(discovered != null, "bash is required for script integration tests");
        return discovered;
    }

    private String discoverBashFromPath() {
        try {
            Process process = new ProcessBuilder("sh", "-lc", "command -v bash")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exit = process.waitFor();
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
}
