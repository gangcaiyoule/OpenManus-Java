package com.openmanus.agent.context;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelContextTokenCounterFactoryTest {

    @Test
    void shouldDefaultToApproxModeWhenModeIsBlankOrUnknown() {
        assertEquals("approx", ModelContextTokenCounterFactory.normalizeMode(null));
        assertEquals("approx", ModelContextTokenCounterFactory.normalizeMode("   "));
        assertEquals("invalid", ModelContextTokenCounterFactory.normalizeMode("invalid"));
        assertSame(
                ApproxModelContextTokenCounter.getInstance(),
                ModelContextTokenCounterFactory.create("invalid")
        );
    }

    @Test
    void shouldFallbackToApproxCounterWhenTokenizerModeIsConfigured() {
        assertEquals("tokenizer", ModelContextTokenCounterFactory.normalizeMode("ToKeNiZeR"));
        assertTrue(ModelContextTokenCounterFactory.create("tokenizer")
                instanceof ModelTokenizerModelContextTokenCounter);
    }

    @Test
    void shouldSelectModelTokenizerEncodingByConfiguredModelFamily() {
        ModelContextTokenCounter counter = ModelContextTokenCounterFactory.create("tokenizer", "gpt-4o-mini");
        assertTrue(counter instanceof ModelTokenizerModelContextTokenCounter);
        ModelTokenizerModelContextTokenCounter tokenizerCounter = (ModelTokenizerModelContextTokenCounter) counter;
        assertEquals(com.knuddels.jtokkit.api.EncodingType.O200K_BASE, tokenizerCounter.encodingType());
    }

    @Test
    void shouldFallbackToDefaultEncodingWhenModelFamilyIsUnknown() {
        ModelContextTokenCounter counter = ModelContextTokenCounterFactory.create("tokenizer", "custom-model-001");
        assertTrue(counter instanceof ModelTokenizerModelContextTokenCounter);
        ModelTokenizerModelContextTokenCounter tokenizerCounter = (ModelTokenizerModelContextTokenCounter) counter;
        assertEquals(com.knuddels.jtokkit.api.EncodingType.CL100K_BASE, tokenizerCounter.encodingType());
    }

    @Test
    void shouldFallbackToLightweightTokenizerWhenModelTokenizerInitFails() {
        assertTrue(
                ModelContextTokenCounterFactory.create(
                        "tokenizer",
                        "gpt-5-mini",
                        () -> {
                            throw new IllegalStateException("model tokenizer unavailable");
                        },
                        TokenizerModelContextTokenCounter::getInstance
                ) instanceof TokenizerModelContextTokenCounter
        );
    }

    @Test
    void shouldFallbackToLightweightTokenizerWhenModelTokenizerThrowsLinkageError() {
        assertTrue(
                ModelContextTokenCounterFactory.create(
                        "tokenizer",
                        "gpt-5-mini",
                        () -> {
                            throw new NoClassDefFoundError("jtokkit missing");
                        },
                        TokenizerModelContextTokenCounter::getInstance
                ) instanceof TokenizerModelContextTokenCounter
        );
    }

    @Test
    void shouldFallbackToApproxWhenAllTokenizerCountersFail() {
        assertSame(
                ApproxModelContextTokenCounter.getInstance(),
                ModelContextTokenCounterFactory.create(
                        "tokenizer",
                        "gpt-5-mini",
                        () -> {
                            throw new IllegalStateException("model tokenizer unavailable");
                        },
                        () -> {
                            throw new IllegalStateException("lightweight tokenizer unavailable");
                        }
                )
        );
        assertSame(
                ApproxModelContextTokenCounter.getInstance(),
                ModelContextTokenCounterFactory.create("tokenizer", () -> null, () -> null)
        );
    }

    @Test
    void shouldFallbackToApproxWhenLightweightTokenizerThrowsLinkageError() {
        assertSame(
                ApproxModelContextTokenCounter.getInstance(),
                ModelContextTokenCounterFactory.create(
                        "tokenizer",
                        "gpt-5-mini",
                        () -> {
                            throw new IllegalStateException("model tokenizer unavailable");
                        },
                        () -> {
                            throw new NoClassDefFoundError("lightweight tokenizer missing");
                        }
                )
        );
    }

    @Test
    void shouldEmitFallbackStageInfoLogWhenFallingBackToLightweightTokenizer() {
        assertTrue(withCapturedFactoryLogs(appender -> {
            ModelContextTokenCounterFactory.create(
                    "tokenizer",
                    "gpt-5-mini",
                    () -> {
                        throw new IllegalStateException("model tokenizer unavailable");
                    },
                    TokenizerModelContextTokenCounter::getInstance
            );
            return hasFallbackStageInfoLog(appender, "tokenizer", "lightweight_tokenizer");
        }));
    }

    @Test
    void shouldEmitFallbackStageInfoLogWhenSelectingModelTokenizer() {
        assertTrue(withCapturedFactoryLogs(appender -> {
            ModelContextTokenCounterFactory.create(
                    "tokenizer",
                    "gpt-5-mini",
                    () -> ModelTokenizerModelContextTokenCounter.forModel("gpt-5-mini"),
                    TokenizerModelContextTokenCounter::getInstance
            );
            return hasFallbackStageInfoLog(appender, "tokenizer", "model_tokenizer");
        }));
    }

    @Test
    void shouldEmitFallbackStageInfoLogWhenFallingBackToApprox() {
        assertTrue(withCapturedFactoryLogs(appender -> {
            ModelContextTokenCounterFactory.create(
                    "tokenizer",
                    "gpt-5-mini",
                    () -> {
                        throw new IllegalStateException("model tokenizer unavailable");
                    },
                    () -> {
                        throw new IllegalStateException("lightweight tokenizer unavailable");
                    }
            );
            return hasFallbackStageInfoLog(appender, "tokenizer", "approx");
        }));
    }

    private static boolean withCapturedFactoryLogs(java.util.function.Function<ListAppender<ILoggingEvent>, Boolean> assertion) {
        Logger logger = (Logger) LoggerFactory.getLogger(ModelContextTokenCounterFactory.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        Level originalLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
        appender.start();
        try {
            return assertion.apply(appender);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(originalLevel);
        }
    }

    private static boolean hasFallbackStageInfoLog(
            ListAppender<ILoggingEvent> appender,
            String expectedMode,
            String expectedFallbackStage) {
        return appender.list.stream().anyMatch(event ->
                event.getLevel() == Level.INFO
                        && hasFieldValue(event, "mode", expectedMode)
                        && hasFieldValue(event, "fallbackStage", expectedFallbackStage));
    }

    private static boolean hasFieldValue(ILoggingEvent event, String key, String expectedValue) {
        if (hasArgument(event, expectedValue)) {
            return true;
        }
        // Defensive fallback for appenders that do not retain argument arrays.
        return hasKeyValueInFormattedMessage(event.getFormattedMessage(), key, expectedValue);
    }

    private static boolean hasArgument(ILoggingEvent event, String expectedValue) {
        Object[] args = event.getArgumentArray();
        if (args != null && args.length > 0) {
            for (Object arg : args) {
                if (matchesArgument(arg, expectedValue)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasKeyValueInFormattedMessage(String formattedMessage, String key, String expectedValue) {
        return formattedMessage != null
                && formattedMessage.contains(key + "=" + expectedValue);
    }

    private static boolean matchesArgument(Object argument, String expected) {
        if (argument instanceof Object[] nested) {
            for (Object nestedArgument : nested) {
                if (matchesArgument(nestedArgument, expected)) {
                    return true;
                }
            }
            return false;
        }
        return expected.equals(String.valueOf(argument));
    }
}
