package com.invoiceapproval.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.stream.Stream;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class RequireRoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }

        RequireRole onClass = method.getBeanType().getAnnotation(RequireRole.class);
        RequireRole onMethod = method.getMethodAnnotation(RequireRole.class);
        UserSession session = (UserSession) request.getAttribute(SessionAuthFilter.SESSION_ATTRIBUTE);

        boolean allowed = Stream.of(onClass, onMethod)
                .filter(rule -> rule != null)
                .allMatch(rule -> session != null && Arrays.asList(rule.value()).contains(session.role()));

        if (!allowed) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        }
        return allowed;
    }
}
