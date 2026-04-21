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
  private static final String SESSION_ID_MDC_KEY = "sessionId";

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    String sessionId =
        normalizeSessionId(request.getHeader(SESSION_ID_HEADER));
    MDC.put(SESSION_ID_MDC_KEY, sessionId);
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    MDC.remove(SESSION_ID_MDC_KEY);
  }

  static String normalizeSessionId(String rawSessionId) {
    return SessionIdPolicy.normalizeOrGenerate(rawSessionId);
  }
}
