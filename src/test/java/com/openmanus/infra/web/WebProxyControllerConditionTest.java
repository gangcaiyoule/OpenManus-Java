package com.openmanus.infra.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WebProxyControllerConditionTest {

    @Test
    void shouldOnlyEnableControllerWhenWebProxyIsExplicitlyEnabled() {
        ConditionalOnProperty condition = WebProxyController.class.getAnnotation(ConditionalOnProperty.class);

        assertNotNull(condition);
        assertEquals("openmanus.web-proxy", condition.prefix());
        assertArrayEquals(new String[]{"enabled"}, condition.name());
        assertEquals("true", condition.havingValue());
    }
}
