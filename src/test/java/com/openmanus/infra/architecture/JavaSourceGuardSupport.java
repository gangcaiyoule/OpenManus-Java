package com.openmanus.infra.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

final class JavaSourceGuardSupport {

    private JavaSourceGuardSupport() {
    }

    static List<String> findTrimmedSanitizedLines(Path javaFile, Predicate<String> predicate) {
        try {
            return sanitize(Files.readString(javaFile)).lines()
                    .map(String::trim)
                    .filter(predicate)
                    .map(line -> javaFile + " -> " + line)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + javaFile, e);
        }
    }

    static List<String> findForbiddenImportPrefixes(Path javaFile, List<String> forbiddenImportPrefixes) {
        return findTrimmedSanitizedLines(javaFile,
                line -> line.startsWith("import ")
                        && forbiddenImportPrefixes.stream().anyMatch(line::startsWith));
    }

    static List<String> findForbiddenQualifiedReferencePrefixes(Path javaFile,
                                                                List<String> forbiddenReferencePrefixes) {
        return findTrimmedSanitizedLines(javaFile,
                line -> !line.startsWith("package ")
                        && forbiddenReferencePrefixes.stream().anyMatch(line::contains));
    }

    static String sanitize(String source) {
        StringBuilder sanitized = new StringBuilder(source.length());
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;
        boolean escape = false;

        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';

            if (inLineComment) {
                if (current == '\n') {
                    inLineComment = false;
                    sanitized.append('\n');
                } else {
                    sanitized.append(' ');
                }
                continue;
            }

            if (inBlockComment) {
                if (current == '*' && next == '/') {
                    sanitized.append("  ");
                    inBlockComment = false;
                    index++;
                } else if (current == '\n') {
                    sanitized.append('\n');
                } else {
                    sanitized.append(' ');
                }
                continue;
            }

            if (inString) {
                if (escape) {
                    sanitized.append(' ');
                    escape = false;
                    continue;
                }
                if (current == '\\') {
                    sanitized.append(' ');
                    escape = true;
                    continue;
                }
                sanitized.append(current == '\n' ? '\n' : ' ');
                if (current == '"') {
                    inString = false;
                }
                continue;
            }

            if (inChar) {
                if (escape) {
                    sanitized.append(' ');
                    escape = false;
                    continue;
                }
                if (current == '\\') {
                    sanitized.append(' ');
                    escape = true;
                    continue;
                }
                sanitized.append(current == '\n' ? '\n' : ' ');
                if (current == '\'') {
                    inChar = false;
                }
                continue;
            }

            if (current == '/' && next == '/') {
                sanitized.append("  ");
                inLineComment = true;
                index++;
                continue;
            }

            if (current == '/' && next == '*') {
                sanitized.append("  ");
                inBlockComment = true;
                index++;
                continue;
            }

            if (current == '"') {
                sanitized.append(' ');
                inString = true;
                continue;
            }

            if (current == '\'') {
                sanitized.append(' ');
                inChar = true;
                continue;
            }

            sanitized.append(current);
        }

        return sanitized.toString();
    }
}
