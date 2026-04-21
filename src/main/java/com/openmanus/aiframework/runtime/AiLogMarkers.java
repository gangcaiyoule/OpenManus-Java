package com.openmanus.aiframework.runtime;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * Runtime-level log markers shared by agent/domain/infra layers.
 */
public final class AiLogMarkers {

    public static final Marker TO_FRONTEND = MarkerFactory.getMarker("TO_FRONTEND");

    private AiLogMarkers() {
    }
}
