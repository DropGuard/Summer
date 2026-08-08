package com.github.dropguard.summer.issuetracker.security;

import com.github.dropguard.summer.issuetracker.common.BusinessException;
import com.github.dropguard.summer.web.RequestContextHolder;

/**
 * Thin accessor for the framework-provided request-scoped current user. Centralizes the
 * "authentication required" contract so controllers and services don't each re-implement the null
 * check.
 */
public final class SecurityContext {

    private SecurityContext() {}

    public static long currentUserId() {
        Long id = RequestContextHolder.currentUserId();
        if (id == null) {
            throw BusinessException.unauthorized("Authentication required");
        }
        return id;
    }
}
