package com.github.dropguard.summer.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageTest {

    @Test
    void ofBuildsPageFromContentTotalAndRequest() {
        Page<String> page = Page.of(List.of("a", "b"), 5L, new PageRequest(0, 2));
        assertEquals(List.of("a", "b"), page.content());
        assertEquals(5L, page.total());
        assertEquals(0, page.page());
        assertEquals(2, page.size());
    }

    @Test
    void ofUsesClampedPageRequestValues() {
        // size 0 -> DEFAULT_SIZE (20) via PageRequest clamp
        Page<String> page = Page.of(List.of(), 0L, new PageRequest(-1, 0));
        assertEquals(20, page.size());
        assertEquals(0, page.page());
    }
}
