package summer.web;

/**
 * Default implementation of the {@link Pageable} interface. Follows the Spring
 * Data JPA PageRequest design.
 *
 * <p>
 * Usage:
 * </p>
 * 
 * <pre>
 * PageRequest pageRequest = PageRequest.of(0, 20);
 * PageRequest pageRequest = PageRequest.of(0, 20, Sort.by("createdAt").descending());
 * </pre>
 *
 * @param page
 *            the zero-based page index
 * @param size
 *            the size of the page
 * @param sort
 *            the sorting parameters
 */
public record PageRequest(int page, int size, Sort sort) implements Pageable {

	/**
	 * Creates a new PageRequest with the given page and size.
	 *
	 * @param page
	 *            the zero-based page index
	 * @param size
	 *            the size of the page
	 * @return a new PageRequest
	 */
	public static PageRequest of(int page, int size) {
		return new PageRequest(page, size, Sort.unsorted());
	}

	/**
	 * Creates a new PageRequest with the given page, size, and sort.
	 *
	 * @param page
	 *            the zero-based page index
	 * @param size
	 *            the size of the page
	 * @param sort
	 *            the sorting parameters
	 * @return a new PageRequest
	 */
	public static PageRequest of(int page, int size, Sort sort) {
		return new PageRequest(page, size, sort);
	}

	@Override
	public int getPageNumber() {
		return page;
	}

	@Override
	public int getPageSize() {
		return size;
	}

	@Override
	public Sort getSort() {
		return sort;
	}

	/**
	 * Returns the offset (number of items to skip) for this page request.
	 *
	 * @return the offset
	 */
	public long getOffset() {
		return (long) page * size;
	}
}
