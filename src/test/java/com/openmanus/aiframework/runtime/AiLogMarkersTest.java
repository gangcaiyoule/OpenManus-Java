package com.openmanus.aiframework.runtime;

import com.openmanus.infra.log.LogMarkers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AiLogMarkersTest {

    @Test
    void shouldKeepFrontendMarkerNameStable() {
        assertEquals("TO_FRONTEND", AiLogMarkers.TO_FRONTEND.getName());
    }

    @Test
    void shouldKeepInfraBridgeMarkerCompatible() {
        assertSame(AiLogMarkers.TO_FRONTEND, LogMarkers.TO_FRONTEND);
    }
}
