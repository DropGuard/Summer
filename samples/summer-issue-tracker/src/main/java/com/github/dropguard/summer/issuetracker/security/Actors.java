package com.github.dropguard.summer.issuetracker.security;

import com.github.dropguard.summer.web.exception.UnauthorizedException;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.RequestAttributes;

/**
 * Reads the current user id from the request attribute the auth middleware set (the Gin {@code
 * c.Get} contract). Centralizes the "authentication required" check so controllers don't each
 * re-implement the null test.
 */
public final class Actors {

    private Actors() {}

    public static long require(HttpContext ctx) {
        Long id = ctx.request().getAttribute(RequestAttributes.USER_ID);
        if (id == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return id;
    }
}
