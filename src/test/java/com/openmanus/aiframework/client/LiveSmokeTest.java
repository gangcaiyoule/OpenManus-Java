package com.openmanus.aiframework.client;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Test
@Tag("live-smoke")
@EnabledIfSystemProperty(
        named = LiveSmokeTest.LIVE_SMOKE_OPT_IN_PROPERTY,
        matches = LiveSmokeTest.LIVE_SMOKE_OPT_IN_PATTERN
)
@interface LiveSmokeTest {

    String LIVE_SMOKE_GROUPS_PATTERN = "(^|.*,|\\s)live-smoke($|,.*|\\s)";
    String LIVE_SMOKE_OPT_IN_PROPERTY = "openmanus.liveSmoke.enabled";
    String LIVE_SMOKE_OPT_IN_PATTERN = "(?i:true)";
}
