package com.openmanus.infra.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class WebMvcConfigTest {

    @Test
    void shouldNotExposeProxyCorsMappingWhenWebProxyIsDisabled() {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getWebProxy().setEnabled(false);
        WebMvcConfig config = new WebMvcConfig(mock(MdcInterceptor.class), properties);
        TestCorsRegistry registry = new TestCorsRegistry();

        config.addCorsMappings(registry);

        assertTrue(registry.configurations().isEmpty());
    }

    @Test
    void shouldNotExposeProxyCorsMappingWhenAllowedOriginsMissing() {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getWebProxy().setEnabled(true);
        properties.getWebProxy().setAllowedOrigins(List.of());
        WebMvcConfig config = new WebMvcConfig(mock(MdcInterceptor.class), properties);
        TestCorsRegistry registry = new TestCorsRegistry();

        config.addCorsMappings(registry);

        assertTrue(registry.configurations().isEmpty());
    }

    @Test
    void shouldRegisterProxyCorsMappingForExplicitAllowedOrigins() {
        OpenManusProperties properties = new OpenManusProperties();
        properties.getWebProxy().setEnabled(true);
        properties.getWebProxy().setAllowedOrigins(List.of("https://app.example.com", "https://admin.example.com"));
        WebMvcConfig config = new WebMvcConfig(mock(MdcInterceptor.class), properties);
        TestCorsRegistry registry = new TestCorsRegistry();

        config.addCorsMappings(registry);

        CorsConfiguration configuration = registry.configurations().get("/api/proxy/**");
        assertEquals(List.of("https://app.example.com", "https://admin.example.com"),
                configuration.getAllowedOrigins());
        assertEquals(List.of("GET"), configuration.getAllowedMethods());
    }

    private static final class TestCorsRegistry extends CorsRegistry {

        Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }
}
