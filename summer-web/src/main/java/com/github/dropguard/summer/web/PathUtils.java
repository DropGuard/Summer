mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.web;

mport com.github.dropguard.summer.core.Internal;
@Internal

mport com.github.dropguard.summer.core.Internal;
/** Shared path normalization utilities for route registration and matching. */
mport com.github.dropguard.summer.core.Internal;
public final class PathUtils {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private PathUtils() {}
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Normalizes a path: ensures leading slash, collapses multiple slashes, removes trailing slash
mport com.github.dropguard.summer.core.Internal;
     * (except root).
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public static String normalizePath(String path) {
mport com.github.dropguard.summer.core.Internal;
        if (path == null || path.isEmpty()) {
mport com.github.dropguard.summer.core.Internal;
            return "/";
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        if (!path.startsWith("/")) {
mport com.github.dropguard.summer.core.Internal;
            path = "/" + path;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        path = path.replaceAll("/+", "/");
mport com.github.dropguard.summer.core.Internal;
        if (path.endsWith("/") && path.length() > 1) {
mport com.github.dropguard.summer.core.Internal;
            path = path.substring(0, path.length() - 1);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return path;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /** Combines a base path and a method path into a single normalized path. */
mport com.github.dropguard.summer.core.Internal;
    public static String combinePaths(String basePath, String methodPath) {
mport com.github.dropguard.summer.core.Internal;
        if (basePath.isEmpty()) {
mport com.github.dropguard.summer.core.Internal;
            return normalizePath(methodPath);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        if (methodPath.isEmpty()) {
mport com.github.dropguard.summer.core.Internal;
            return normalizePath(basePath);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        String normalizedBase = normalizePath(basePath);
mport com.github.dropguard.summer.core.Internal;
        String normalizedMethod = normalizePath(methodPath);
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        if (normalizedBase.endsWith("/") && normalizedMethod.startsWith("/")) {
mport com.github.dropguard.summer.core.Internal;
            return normalizedBase + normalizedMethod.substring(1);
mport com.github.dropguard.summer.core.Internal;
        } else if (!normalizedBase.endsWith("/") && !normalizedMethod.startsWith("/")) {
mport com.github.dropguard.summer.core.Internal;
            return normalizedBase + "/" + normalizedMethod;
mport com.github.dropguard.summer.core.Internal;
        } else {
mport com.github.dropguard.summer.core.Internal;
            return normalizedBase + normalizedMethod;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
