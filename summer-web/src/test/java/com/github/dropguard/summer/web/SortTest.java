package com.github.dropguard.summer.web;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.web.Sort.Direction;
import com.github.dropguard.summer.web.Sort.Order;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Sort builders and order transformations (jacoco gap: Sort was 0% — no tests existed). */
class SortTest {

    @Test
    void byCreatesAscendingOrdersInGivenOrder() {
        Sort sort = Sort.by("name", "age");
        assertEquals(
                List.of(new Order("name", Direction.ASC), new Order("age", Direction.ASC)),
                sort.orders());
    }

    @Test
    void unsortedIsNotSorted() {
        Sort sort = Sort.unsorted();
        assertFalse(sort.isSorted());
        assertTrue(sort.orders().isEmpty());
    }

    @Test
    void descendingFlipsEveryOrder() {
        Sort sort = Sort.by("name").descending();
        assertEquals(List.of(new Order("name", Direction.DESC)), sort.orders());
    }

    @Test
    void ascendingFlipsBack() {
        Sort sort = Sort.by("name").descending().ascending();
        assertEquals(List.of(new Order("name", Direction.ASC)), sort.orders());
    }

    @Test
    void parseReadsPropertyDirectionPairs() {
        Sort sort = Sort.parse("name,DESC,age,ASC");
        assertEquals(
                List.of(new Order("name", Direction.DESC), new Order("age", Direction.ASC)),
                sort.orders());
    }

    @Test
    void parseBlankYieldsUnsorted() {
        assertFalse(Sort.parse("  ").isSorted());
        assertFalse(Sort.parse(null).isSorted());
    }
}
