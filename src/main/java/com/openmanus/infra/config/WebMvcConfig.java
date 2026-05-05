package com.openmanus.infra.config;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC配置类.
 *
 * <p>负责注册Web拦截器。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  private final MdcInterceptor mdcInterceptor;
  private final OpenManusProperties properties;

  public WebMvcConfig(MdcInterceptor mdcInterceptor, OpenManusProperties properties) {
    this.mdcInterceptor = mdcInterceptor;
    this.properties = properties;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(mdcInterceptor).addPathPatterns("/api/**");
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    OpenManusProperties.WebProxyConfig webProxy = properties.getWebProxy();
    if (webProxy == null || !webProxy.isEnabled()) {
      return;
    }
    List<String> allowedOrigins = webProxy.getAllowedOrigins();
    if (allowedOrigins == null || allowedOrigins.isEmpty()) {
      return;
    }
    registry.addMapping("/api/proxy/**")
        .allowedOrigins(allowedOrigins.toArray(String[]::new))
        .allowedMethods("GET");
  }
}
