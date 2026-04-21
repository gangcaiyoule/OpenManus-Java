package com.openmanus.aiframework.client;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveSmokeTestContractTest {

    private static final Path POM_XML = Path.of("pom.xml");

    @Test
    void shouldKeepLiveSmokeMetaAnnotationBoundToExplicitGroupsOptIn() throws Exception {
        Tag tag = LiveSmokeTest.class.getAnnotation(Tag.class);
        assertNotNull(tag);
        assertEquals("live-smoke", tag.value());

        EnabledIfSystemProperty enabledIf = LiveSmokeTest.class.getAnnotation(EnabledIfSystemProperty.class);
        assertNotNull(enabledIf);
        assertEquals(LiveSmokeTest.LIVE_SMOKE_OPT_IN_PROPERTY, enabledIf.named());
        assertEquals(LiveSmokeTest.LIVE_SMOKE_OPT_IN_PATTERN, enabledIf.matches());
    }

    @Test
    void shouldUseLiveSmokeMetaAnnotationOnAllLiveSmokeTestEntrypoints() throws Exception {
        assertLiveSmokeEntryPoint(OpenAiClientLiveSmokeTest.class,
                "shouldCallLiveCompatibleOpenAiEndpointForChatAndStream");
        assertLiveSmokeEntryPoint(AnthropicClientLiveSmokeTest.class,
                "shouldCallLiveAnthropicEndpointForChatAndStream");
        assertLiveSmokeEntryPoint(GeminiClientLiveSmokeTest.class,
                "shouldCallLiveGeminiEndpointForChatAndStream");
    }

    private static void assertLiveSmokeEntryPoint(Class<?> type, String methodName) throws Exception {
        Tag classTag = type.getAnnotation(Tag.class);
        assertNotNull(classTag, () -> type.getSimpleName() + " must stay in the live-smoke tag set for surefire exclusion");
        assertEquals("live-smoke", classTag.value());

        EnabledIfSystemProperty classEnabledIf = type.getAnnotation(EnabledIfSystemProperty.class);
        assertNotNull(classEnabledIf, () -> type.getSimpleName() + " must keep class-level system-property gating as a backup guard");
        assertEquals(LiveSmokeTest.LIVE_SMOKE_OPT_IN_PROPERTY, classEnabledIf.named());
        assertEquals(LiveSmokeTest.LIVE_SMOKE_OPT_IN_PATTERN, classEnabledIf.matches());

        Method method = type.getDeclaredMethod(methodName);
        LiveSmokeTest annotation = method.getAnnotation(LiveSmokeTest.class);
        assertNotNull(annotation, () -> type.getSimpleName() + "." + methodName + " must require explicit live-smoke opt-in");
    }

    @Test
    void shouldKeepSurefireDefaultingLiveSmokeOptInToFalse() throws Exception {
        String pom = Files.readString(POM_XML);

        assertTrue(pom.contains("<openmanus.liveSmoke.enabled>false</openmanus.liveSmoke.enabled>"),
                "pom.xml must default live smoke opt-in to false for regular test runs");
        assertTrue(pom.contains("<systemPropertyVariables>"),
                "pom.xml must forward the live smoke opt-in property into surefire");
        assertTrue(pom.contains("<openmanus.liveSmoke.enabled>${openmanus.liveSmoke.enabled}</openmanus.liveSmoke.enabled>"),
                "pom.xml must let explicit live smoke scripts override the default surefire opt-in");
    }
}
