package com.github.dropguard.summer.issuetracker.issue;

import java.util.List;

/** Standard paginated result: the page content plus enough metadata for the UI. */
public record Page<T>(List<T> content, long total, int page, int size) {
    public static <T> Page<T> of(List<T> content, long total, PageRequest req) {
        return new Page<>(content, total, req.page(), req.size());
    }
}
