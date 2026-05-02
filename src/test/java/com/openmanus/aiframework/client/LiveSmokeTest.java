package com.openmanus.aiframework.client;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation for live smoke tests that require real API calls.
 * Live smoke tests are disabled by default and must be explicitly enabled.
 *
 * Enable with: -Dopenmanus.liveSmoke.enabled=true
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Tag("live-smoke")
@Test
public @interface LiveSmokeTest {

    String LIVE_SMOKE_OPT_IN_PROPERTY = "openmanus.liveSmoke.enabled";

    String LIVE_SMOKE_OPT_IN_PATTERN = "true";
}
