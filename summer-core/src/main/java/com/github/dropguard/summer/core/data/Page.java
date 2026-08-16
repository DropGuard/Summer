package com.github.dropguard.summer.core.data;

import java.util.List;

/**
 * Standard paginated result: the page content plus enough metadata for the UI to render pagination.
 *
 * @param content the rows of this page
 * @param total the total number of matching rows across all pages
 * @param page the zero-based page index of this page
 * @param size the page size
 */
public record Page<T>(List<T> content, long total, int page, int size) {
    public static <T> Page<T> of(List<T> content, long total, PageRequest req) {
        return new Page<>(content, total, req.page(), req.size());
    }
}
