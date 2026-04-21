package com.openmanus.agent.context;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.knuddels.jtokkit.api.EncodingType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelTokenizerEncodingMapperTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("modelToEncodingCases")
    void shouldResolveEncodingByModelFamily(String modelName, EncodingType expectedEncoding) {
        assertEquals(expectedEncoding, ModelTokenizerEncodingMapper.resolve(modelName));
    }

    @Test
    void shouldFallbackToDefaultEncodingWhenModelNameIsNullOrBlank() {
        assertEquals(EncodingType.CL100K_BASE, ModelTokenizerEncodingMapper.resolve(null));
        assertEquals(EncodingType.CL100K_BASE, ModelTokenizerEncodingMapper.resolve("   "));
    }

    @Test
    void shouldEmitResolvedEncodingDebugLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(ModelTokenizerEncodingMapper.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        Level originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        logger.addAppender(appender);
        appender.start();
        try {
            ModelTokenizerEncodingMapper.resolve("gpt-5-mini");
            assertTrue(
                    appender.list.stream().anyMatch(event ->
                            event.getLevel() == Level.DEBUG
                                    && event.getFormattedMessage().contains("model-context tokenizer encoding resolved")
                                    && event.getFormattedMessage().contains("encoding=O200K_BASE")
                    )
            );
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(originalLevel);
        }
    }

    private static Stream<Arguments> modelToEncodingCases() {
        return Stream.of(
                // Modern model families.
                Arguments.of("gpt-5-mini", EncodingType.O200K_BASE),
                Arguments.of("gpt_4o_mini", EncodingType.O200K_BASE),
                Arguments.of("claude-3-7-sonnet", EncodingType.O200K_BASE),
                Arguments.of("gemini-2.5-pro", EncodingType.O200K_BASE),
                Arguments.of("deepseek-chat", EncodingType.O200K_BASE),

                // Legacy model families.
                Arguments.of("codex-cushman-001", EncodingType.P50K_BASE),
                Arguments.of("text-davinci-003", EncodingType.R50K_BASE),

                // Unknown model fallback.
                Arguments.of("custom-private-model", EncodingType.CL100K_BASE)
        );
    }
}
