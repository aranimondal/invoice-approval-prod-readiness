package com.invoiceapproval.api.auth;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Signed-in sessions in memory, keyed by an opaque bearer token. No expiry, same as the original. */
@Component
public class SessionStore {

    private final SecureRandom random = new SecureRandom();
    private final Map<String, UserSession> sessions = new ConcurrentHashMap<>();

    public String create(UserSession session) {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String token = HexFormat.of().withUpperCase().formatHex(bytes);
        sessions.put(token, session);
        return token;
    }

    public Optional<UserSession> get(String token) {
        return token == null ? Optional.empty() : Optional.ofNullable(sessions.get(token));
    }

    public void remove(String token) {
        sessions.remove(token);
    }
}
