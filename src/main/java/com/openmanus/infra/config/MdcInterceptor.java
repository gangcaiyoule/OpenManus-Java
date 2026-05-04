package com.openmanus.infra.config;

import com.openmanus.domain.service.SessionIdPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * MDC拦截器.
 *
 * <p>负责在请求开始时设置唯一会话ID，并在请求结束时清理，确保日志追踪的线程安全性。
 */
@Component
public class MdcInterceptor implements HandlerInterceptor {

  private static final String SESSION_ID_HEADER = "X-Session-ID";
  private static final String USER_ID_HEADER = "X-User-ID";
  private static final String SESSION_ID_MDC_KEY = "sessionId";
  private static final String USER_ID_MDC_KEY = "userId";
  private static final String DEFAULT_USER_ID = "001";

  private final OpenManusProperties properties;

  public MdcInterceptor(OpenManusProperties properties) {
    this.properties = properties;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    String sessionId =
        normalizeSessionId(
            request.getHeader(SESSION_ID_HEADER),
            request.getHeader(USER_ID_HEADER),
            defaultUserId());
    String userId = normalizeUserId(request.getHeader(USER_ID_HEADER), defaultUserId());
    MDC.put(SESSION_ID_MDC_KEY, sessionId);
    MDC.put(USER_ID_MDC_KEY, userId);
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    MDC.remove(SESSION_ID_MDC_KEY);
    MDC.remove(USER_ID_MDC_KEY);
  }

  static String normalizeSessionId(String rawSessionId, String rawUserId, String defaultUserId) {
    String sessionId = SessionIdPolicy.normalizeOrNull(rawSessionId);
    if (sessionId != null) {
      return sessionId;
    }
    String userId = SessionIdPolicy.normalizeOrNull(rawUserId);
    if (userId != null) {
      return userId;
    }
    String configuredDefault = SessionIdPolicy.normalizeOrNull(defaultUserId);
    return configuredDefault == null ? DEFAULT_USER_ID : configuredDefault;
  }

  static String normalizeUserId(String rawUserId, String defaultUserId) {
    String userId = SessionIdPolicy.normalizeOrNull(rawUserId);
    if (userId != null) {
      return userId;
    }
    String configuredDefault = SessionIdPolicy.normalizeOrNull(defaultUserId);
    return configuredDefault == null ? DEFAULT_USER_ID : configuredDefault;
  }

  private String defaultUserId() {
    if (properties == null || properties.getApp() == null) {
      return DEFAULT_USER_ID;
    }
    return properties.getApp().getDefaultUserId();
  }
}
