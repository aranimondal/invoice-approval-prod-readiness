package com.invoiceapproval.api.auth;

import jakarta.servlet.http.HttpServletRequest;

public final class CurrentSession {

    private CurrentSession() {
    }

    public static UserSession of(HttpServletRequest request) {
        return (UserSession) request.getAttribute(SessionAuthFilter.SESSION_ATTRIBUTE);
    }
}
