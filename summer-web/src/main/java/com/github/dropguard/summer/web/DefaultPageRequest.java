package com.github.dropguard.summer.web;

import com.github.dropguard.summer.core.data.PageRequest;

/**
 * Default {@link ScrollRequest} carrying zero-based {@code page} and {@code size}.
 *
 * <p>Delegates to {@link PageRequest} so the web layer inherits the core pagination contract —
 * clamping of negative pages, default size, and the {@link PageRequest#MAX_SIZE maximum page size}
 * — instead of re-implementing it with subtly different semantics. {@link #delegate()} exposes the
 * underlying core request for query layers that operate on {@code core.data.PageRequest}.
 */
public record DefaultPageRequest(PageRequest delegate) implements ScrollRequest {

    /** Convenience constructor for callers that have raw {@code page}/{@code size}. */
    public DefaultPageRequest(int page, int size) {
        this(new PageRequest(page, size));
    }

    /** The zero-based page index. */
    public int page() {
        return delegate.page();
    }

    /** The page size (clamped to the core {@link PageRequest#MAX_SIZE}). */
    public int size() {
        return delegate.size();
    }

    /** The zero-based offset ({@code page * size}). */
    public int offset() {
        return delegate.offset();
    }

    /** The underlying core pagination request. */
    public PageRequest asPageRequest() {
        return delegate;
    }
}
