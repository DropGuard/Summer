package com.github.dropguard.summer.realworld.auth;

import com.github.dropguard.summer.web.HttpContext;

/**
 * Utility class for authentication-related operations.
 * Extracts common authentication logic from controllers.
 */
public final class AuthUtils {

	private AuthUtils() {
		// Utility class, no instantiation
	}

	/**
	 * Extracts the JWT token from the Authorization header.
	 * Expected format: "Token <jwt>"
	 *
	 * @param ctx the HTTP context
	 * @return the token string, or null if not present/invalid format
	 */
	public static String extractToken(HttpContext ctx) {
		String authHeader = ctx.header("Authorization");
		if (authHeader != null && authHeader.startsWith("Token ")) {
			return authHeader.substring(6);
		}
		return null;
	}

	/**
	 * Validates the access token and returns the authenticated user's ID.
	 * Use when authentication is required.
	 *
	 * @throws BusinessException 401 if the token is missing, expired, or invalid
	 */
	public static Long getCurrentUserId(HttpContext ctx, JwtUtil jwtUtil) {
		String token = extractToken(ctx);
		return jwtUtil.validateAccessToken(token);
	}

	/**
	 * Returns the authenticated user's ID, or {@code null} when no token is
	 * present (anonymous access). If a token is supplied it must be valid —
	 * expired or malformed tokens throw a 401 instead of silently falling back
	 * to null.
	 */
	public static Long tryGetCurrentUserId(HttpContext ctx, JwtUtil jwtUtil) {
		String token = extractToken(ctx);
		if (token == null) return null;
		return jwtUtil.validateAccessToken(token);
	}

	/**
	 * Checks if the request has a valid access token.
	 *
	 * @param ctx the HTTP context
	 * @param jwtUtil the JWT utility for token parsing
	 * @return true if a valid access token is present
	 */
	public static boolean isAuthenticated(HttpContext ctx, JwtUtil jwtUtil) {
		return tryGetCurrentUserId(ctx, jwtUtil) != null;
	}
}
