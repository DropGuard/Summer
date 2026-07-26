package com.github.dropguard.summer.web;

/**
 * Shared path normalization utilities for route registration and matching.
 */
public final class PathUtils {

	private PathUtils() {
	}

	/**
	 * Normalizes a path: ensures leading slash, collapses multiple slashes, removes
	 * trailing slash (except root).
	 */
	public static String normalizePath(String path) {
		if (path == null || path.isEmpty()) {
			return "/";
		}
		if (!path.startsWith("/")) {
			path = "/" + path;
		}
		path = path.replaceAll("/+", "/");
		if (path.endsWith("/") && path.length() > 1) {
			path = path.substring(0, path.length() - 1);
		}
		return path;
	}

	/**
	 * Combines a base path and a method path into a single normalized path.
	 */
	public static String combinePaths(String basePath, String methodPath) {
		if (basePath.isEmpty()) {
			return normalizePath(methodPath);
		}
		if (methodPath.isEmpty()) {
			return normalizePath(basePath);
		}

		String normalizedBase = normalizePath(basePath);
		String normalizedMethod = normalizePath(methodPath);

		if (normalizedBase.endsWith("/") && normalizedMethod.startsWith("/")) {
			return normalizedBase + normalizedMethod.substring(1);
		} else if (!normalizedBase.endsWith("/") && !normalizedMethod.startsWith("/")) {
			return normalizedBase + "/" + normalizedMethod;
		} else {
			return normalizedBase + normalizedMethod;
		}
	}
}
