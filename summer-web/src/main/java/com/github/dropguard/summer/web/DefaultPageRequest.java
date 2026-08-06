package com.github.dropguard.summer.web;

/** Default {@link ScrollRequest} carrying zero-based {@code page} and {@code size}. */
public record DefaultPageRequest(int page, int size) implements ScrollRequest {}
