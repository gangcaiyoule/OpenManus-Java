package com.openmanus.infra.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DotenvLoaderTest {

    private static final String MODEL_KEY = "DOTENV_LOADER_TEST_MODEL";
    private static final String BASE_URL_KEY = "DOTENV_LOADER_TEST_BASE_URL";
    private static final String API_KEY_KEY = "DOTENV_LOADER_TEST_API_KEY";

    @TempDir
    Path tempDir;

    @Test
    void shouldStripInlineCommentsFromUnquotedValues() throws IOException {
        Path dotenv = tempDir.resolve(".env");
        Files.writeString(dotenv, """
                DOTENV_LOADER_TEST_MODEL=openai-model # preferred model
                DOTENV_LOADER_TEST_BASE_URL=https://openai.example/v1 # endpoint
                DOTENV_LOADER_TEST_API_KEY=openai-key # credential note
                """, StandardCharsets.UTF_8);

        withClearedProperties(() -> {
            DotenvLoader.load(dotenv);

            assertEquals("openai-model", System.getProperty(MODEL_KEY));
            assertEquals("https://openai.example/v1", System.getProperty(BASE_URL_KEY));
            assertEquals("openai-key", System.getProperty(API_KEY_KEY));
        }, MODEL_KEY, BASE_URL_KEY, API_KEY_KEY);
    }

    @Test
    void shouldStripTrailingCommentsFromQuotedValues() throws IOException {
        Path dotenv = tempDir.resolve(".env");
        Files.writeString(dotenv, """
                DOTENV_LOADER_TEST_MODEL="openai-model" # preferred model
                DOTENV_LOADER_TEST_BASE_URL='https://openai.example/v1' # endpoint
                DOTENV_LOADER_TEST_API_KEY="openai-key" # credential note
                """, StandardCharsets.UTF_8);

        withClearedProperties(() -> {
            DotenvLoader.load(dotenv);

            assertEquals("openai-model", System.getProperty(MODEL_KEY));
            assertEquals("https://openai.example/v1", System.getProperty(BASE_URL_KEY));
            assertEquals("openai-key", System.getProperty(API_KEY_KEY));
        }, MODEL_KEY, BASE_URL_KEY, API_KEY_KEY);
    }

    @Test
    void shouldKeepHashInsideUnquotedValueWhenItIsNotAComment() throws IOException {
        Path dotenv = tempDir.resolve(".env");
        Files.writeString(dotenv, """
                DOTENV_LOADER_TEST_BASE_URL=https://openai.example/v1#fragment
                """, StandardCharsets.UTF_8);

        withClearedProperties(() -> {
            DotenvLoader.load(dotenv);

            assertEquals("https://openai.example/v1#fragment", System.getProperty(BASE_URL_KEY));
        }, BASE_URL_KEY);
    }

    @Test
    void shouldTreatCommentOnlyValueAsBlank() throws IOException {
        Path dotenv = tempDir.resolve(".env");
        Files.writeString(dotenv, """
                DOTENV_LOADER_TEST_MODEL=# comment only
                """, StandardCharsets.UTF_8);

        withClearedProperties(() -> {
            DotenvLoader.load(dotenv);

            assertEquals("", System.getProperty(MODEL_KEY));
        }, MODEL_KEY);
    }

    @Test
    void shouldKeepExistingSystemPropertyUnchanged() throws IOException {
        Path dotenv = tempDir.resolve(".env");
        Files.writeString(dotenv, """
                DOTENV_LOADER_TEST_MODEL=dotenv-model # preferred model
                """, StandardCharsets.UTF_8);

        withClearedProperties(() -> {
            System.setProperty(MODEL_KEY, "existing-model");

            DotenvLoader.load(dotenv);

            assertEquals("existing-model", System.getProperty(MODEL_KEY));
        }, MODEL_KEY);
    }

    @Test
    void shouldBackfillBlankSystemPropertyFromDotenv() throws IOException {
        Path dotenv = tempDir.resolve(".env");
        Files.writeString(dotenv, """
                DOTENV_LOADER_TEST_MODEL=dotenv-model
                DOTENV_LOADER_TEST_BASE_URL=https://dotenv.example/v1
                DOTENV_LOADER_TEST_API_KEY=dotenv-key
                """, StandardCharsets.UTF_8);

        withClearedProperties(() -> {
            System.setProperty(MODEL_KEY, "   ");
            System.setProperty(BASE_URL_KEY, "");
            System.setProperty(API_KEY_KEY, " ");

            DotenvLoader.load(dotenv);

            assertEquals("dotenv-model", System.getProperty(MODEL_KEY));
            assertEquals("https://dotenv.example/v1", System.getProperty(BASE_URL_KEY));
            assertEquals("dotenv-key", System.getProperty(API_KEY_KEY));
        }, MODEL_KEY, BASE_URL_KEY, API_KEY_KEY);
    }

    private void withClearedProperties(ThrowingRunnable runnable, String... keys) throws IOException {
        try {
            for (String key : keys) {
                System.clearProperty(key);
            }
            runnable.run();
        } catch (Exception e) {
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException(e);
        } finally {
            for (String key : keys) {
                System.clearProperty(key);
                assertNull(System.getProperty(key));
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
