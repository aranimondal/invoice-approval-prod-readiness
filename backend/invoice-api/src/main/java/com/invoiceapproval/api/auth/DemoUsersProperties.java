package com.invoiceapproval.api.auth;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Demo users from application.yml. Plaintext and shipped with the build: fine for a local demo,
 * and the first thing to replace (Entra ID / Cognito) before production. See target-state.md.
 */
@ConfigurationProperties(prefix = "demo")
public record DemoUsersProperties(List<DemoUser> users) {

    public DemoUsersProperties {
        users = users == null ? List.of() : List.copyOf(users);
    }

    public record DemoUser(String username, String password, String role) {
    }
}
