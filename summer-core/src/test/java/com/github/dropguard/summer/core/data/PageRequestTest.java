package com.github.dropguard.summer.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PageRequestTest {

    @Test
    void clampsNegativePageToZero() {
        assertEquals(0, new PageRequest(-5, 20).page());
    }

    @Test
    void usesDefaultSizeWhenSizeNonPositive() {
        assertEquals(PageRequest.DEFAULT_SIZE, new PageRequest(0, 0).size());
        assertEquals(PageRequest.DEFAULT_SIZE, new PageRequest(0, -1).size());
    }

    @Test
    void clampsSizeToMax() {
        assertEquals(PageRequest.MAX_SIZE, new PageRequest(0, 1000).size());
    }

    @Test
    void preservesValidValues() {
        PageRequest req = new PageRequest(2, 15);
        assertEquals(2, req.page());
        assertEquals(15, req.size());
    }

    @Test
    void offsetIsPageTimesSize() {
        assertEquals(0, new PageRequest(0, 20).offset());
        assertEquals(40, new PageRequest(2, 20).offset());
        assertEquals(30, new PageRequest(1, 30).offset());
    }
}
