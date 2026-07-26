package com.github.dropguard.summer.realworld.common;

import com.github.dropguard.summer.web.ScrollRequest;
import java.util.List;

public record LimitOffsetPageable(int limit, int offset) implements ScrollRequest {

    public <T> List<T> paginate(List<T> list) {
        if (list == null) return null;
        int fromIndex = Math.min(offset, list.size());
        int toIndex = Math.min(offset + limit, list.size());
        return list.subList(fromIndex, toIndex);
    }
}
