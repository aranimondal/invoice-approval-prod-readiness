package com.invoiceapproval.api.web;

import com.invoiceapproval.api.auth.DemoUsersProperties;
import com.invoiceapproval.api.auth.SessionStore;
import com.invoiceapproval.api.auth.UserSession;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final DemoUsersProperties demoUsers;
    private final SessionStore sessions;

    public AuthController(DemoUsersProperties demoUsers, SessionStore sessions) {
        this.demoUsers = demoUsers;
        this.sessions = sessions;
    }

    public record LoginRequest(String username, String password) {
    }

    public record LoginResponse(String token, String username, String role) {
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        return demoUsers.users().stream()
                .filter(user -> user.username().equals(request.username()) && user.password().equals(request.password()))
                .findFirst()
                .<ResponseEntity<?>>map(user -> {
                    String token = sessions.create(new UserSession(user.username(), user.role()));
                    return ResponseEntity.ok(new LoginResponse(token, user.username(), user.role()));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid username or password.")));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            sessions.remove(header.substring("Bearer ".length()));
        }
        return ResponseEntity.noContent().build();
    }
}
