package com.coolapp.medibook.common.security;

import com.coolapp.medibook.common.exception.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class CurrentUser {

    private CurrentUser() {}

    public static Optional<AuthUser> optional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static AuthUser get() {
        return optional().orElseThrow(() -> ApiException.unauthorized("Authentication required"));
    }
}
