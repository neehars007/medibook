package com.coolapp.medibook.common.security;

import com.coolapp.medibook.auth.Role;

import java.util.UUID;

/** The authenticated principal placed in the SecurityContext by {@link JwtAuthFilter}. */
public record AuthUser(UUID id, String email, Role role, String sessionId) {

    public boolean is(Role r) {
        return role == r;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN || role == Role.SUPER_ADMIN;
    }
}
