package com.openmanus.infra.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaSourceGuardSupportTest {

    @TempDir
    Path tempDir;

    @Test
    void sanitizeShouldIgnoreCommentsStringsAndChars() {
        String source = """
                package sample;
                // com.openmanus.domain.FakeInComment
                class Demo {
                    String text = "com.openmanus.domain.FakeInString";
                    char marker = '/';
                    /* com.openmanus.domain.FakeInBlockComment */
                    com.openmanus.domain.RealUsage usage;
                }
                """;

        String sanitized = JavaSourceGuardSupport.sanitize(source);

        assertFalse(sanitized.contains("FakeInComment"));
        assertFalse(sanitized.contains("FakeInString"));
        assertFalse(sanitized.contains("FakeInBlockComment"));
        assertTrue(sanitized.contains("com.openmanus.domain.RealUsage"));
    }

    @Test
    void findForbiddenImportPrefixesShouldOnlyReportRealImports() throws IOException {
        Path javaFile = writeJava("""
                package sample;

                // import com.openmanus.domain.CommentOnly;
                import com.openmanus.domain.RealImport;

                class Demo {
                    String text = "import com.openmanus.domain.StringOnly;";
                }
                """);

        List<String> violations = JavaSourceGuardSupport.findForbiddenImportPrefixes(
                javaFile,
                List.of("import com.openmanus.domain."));

        assertEquals(1, violations.size());
        assertTrue(violations.getFirst().contains("import com.openmanus.domain.RealImport;"));
    }

    @Test
    void findForbiddenQualifiedReferencePrefixesShouldReportFullyQualifiedUsage() throws IOException {
        Path javaFile = writeJava("""
                package sample;

                class Demo {
                    void run() {
                        com.openmanus.domain.RealUsage.execute();
                        String text = "com.openmanus.domain.StringOnly";
                        // com.openmanus.domain.CommentOnly.execute();
                    }
                }
                """);

        List<String> violations = JavaSourceGuardSupport.findForbiddenQualifiedReferencePrefixes(
                javaFile,
                List.of("com.openmanus.domain."));

        assertEquals(1, violations.size());
        assertTrue(violations.getFirst().contains("com.openmanus.domain.RealUsage.execute();"));
    }

    @Test
    void findForbiddenQualifiedReferencePrefixesShouldIgnorePackageDeclaration() throws IOException {
        Path javaFile = writeJava("""
                package com.openmanus.domain.sample;

                class Demo {
                }
                """);

        List<String> violations = JavaSourceGuardSupport.findForbiddenQualifiedReferencePrefixes(
                javaFile,
                List.of("com.openmanus.domain."));

        assertTrue(violations.isEmpty());
    }

    private Path writeJava(String source) throws IOException {
        Path javaFile = tempDir.resolve("Demo.java");
        Files.writeString(javaFile, source);
        return javaFile;
    }
}
