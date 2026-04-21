package com.openmanus.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;

/**
 * Jackson配置类.
 *
 * <p>提供WebSocket消息转换器配置；HTTP JSON序列化配置统一在 application.yaml 中管理。
 */
@Configuration
public class JacksonConfig {

  /**
   * WebSocket消息转换器.
   *
   * <p>复用Spring Boot自动配置的ObjectMapper，保持序列化行为一致。
   */
  @Bean
  public MappingJackson2MessageConverter mappingJackson2MessageConverter(
      ObjectMapper objectMapper) {
    MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
    converter.setObjectMapper(objectMapper);
    return converter;
  }
}
