package summer.web;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Page} and {@link Pageable#paginate(List)}.
 */
class PageTest {

	@Test
	void shouldPaginateFirstPage() {
		Pageable pageable = PageRequest.of(0, 10);
		List<String> items = List.of("a", "b", "c", "d", "e");

		Page<String> page = pageable.paginate(items);

		assertEquals(List.of("a", "b", "c", "d", "e"), page.content());
		assertEquals(5, page.totalElements());
		assertEquals(1, page.getTotalPages());
		assertTrue(page.isFirst());
		assertTrue(page.isLast());
		assertFalse(page.hasNext());
		assertFalse(page.hasPrevious());
	}

	@Test
	void shouldPaginateMiddlePage() {
		Pageable pageable = PageRequest.of(1, 2);
		List<String> items = List.of("a", "b", "c", "d", "e");

		Page<String> page = pageable.paginate(items);

		assertEquals(List.of("c", "d"), page.content());
		assertEquals(5, page.totalElements());
		assertEquals(3, page.getTotalPages());
		assertFalse(page.isFirst());
		assertFalse(page.isLast());
		assertTrue(page.hasNext());
		assertTrue(page.hasPrevious());
	}

	@Test
	void shouldPaginateLastPage() {
		Pageable pageable = PageRequest.of(2, 2);
		List<String> items = List.of("a", "b", "c", "d", "e");

		Page<String> page = pageable.paginate(items);

		assertEquals(List.of("e"), page.content());
		assertEquals(5, page.totalElements());
		assertEquals(3, page.getTotalPages());
		assertFalse(page.isFirst());
		assertTrue(page.isLast());
		assertFalse(page.hasNext());
		assertTrue(page.hasPrevious());
	}

	@Test
	void shouldHandleEmptyList() {
		Pageable pageable = PageRequest.of(0, 10);
		List<String> items = List.of();

		Page<String> page = pageable.paginate(items);

		assertTrue(page.content().isEmpty());
		assertEquals(0, page.totalElements());
		assertEquals(0, page.getTotalPages());
		assertTrue(page.isFirst());
		assertTrue(page.isLast());
	}

	@Test
	void shouldHandlePageBeyondContent() {
		Pageable pageable = PageRequest.of(10, 10);
		List<String> items = List.of("a", "b");

		Page<String> page = pageable.paginate(items);

		assertTrue(page.content().isEmpty());
		assertEquals(2, page.totalElements());
		assertEquals(1, page.getTotalPages());
	}

	@Test
	void shouldReportNumberOfElements() {
		Pageable pageable = PageRequest.of(0, 10);
		List<String> items = List.of("a", "b", "c");

		Page<String> page = pageable.paginate(items);

		assertEquals(3, page.getNumberOfElements());
		assertTrue(page.hasContent());
	}

	@Test
	void shouldHandleUnpaged() {
		Pageable pageable = Pageable.UNPAGED;
		List<String> items = List.of("a", "b", "c");

		Page<String> page = pageable.paginate(items);

		assertEquals(3, page.content().size());
		assertEquals(3, page.totalElements());
	}
}
