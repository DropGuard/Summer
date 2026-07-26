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
	 * Gets the current user ID from the JWT token in the Authorization header.
	 * Returns null if token is missing, invalid, or not an access token.
	 *
	 * @param ctx the HTTP context
	 * @param jwtUtil the JWT utility for token parsing
	 * @return the user ID, or null if not authenticated
	 */
	public static Long getCurrentUserId(HttpContext ctx, JwtUtil jwtUtil) {
		String token = extractToken(ctx);
		if (token == null) {
			return null;
		}
		if (!jwtUtil.isAccessToken(token)) {
			return null;
		}
		try {
			return jwtUtil.getUserIdFromToken(token);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Checks if the request has a valid access token.
	 *
	 * @param ctx the HTTP context
	 * @param jwtUtil the JWT utility for token parsing
	 * @return true if a valid access token is present
	 */
	public static boolean isAuthenticated(HttpContext ctx, JwtUtil jwtUtil) {
		return getCurrentUserId(ctx, jwtUtil) != null;
	}
}
