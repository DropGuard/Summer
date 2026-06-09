package summer.web;

import java.util.List;

/**
 * A page of results returned by {@link Pageable#paginate(List)}.
 *
 * <p>
 * Follows the Spring Data JPA Page interface design. Contains the content
 * (paginated subset), total element count, and the pageable used to produce
 * this page.
 * </p>
 *
 * @param content
 *            the content of this page
 * @param totalElements
 *            total number of elements across all pages
 * @param pageable
 *            the pageable used to produce this page
 * @param <T>
 *            the element type
 */
public record Page<T>(List<T> content, long totalElements, Pageable pageable) {

	/**
	 * Returns the total number of pages.
	 *
	 * @return total pages
	 */
	public int getTotalPages() {
		if (pageable.getPageSize() <= 0) {
			return 0;
		}
		return (int) Math.ceil((double) totalElements / pageable.getPageSize());
	}

	/**
	 * Returns whether there is a next page.
	 *
	 * @return true if next page exists
	 */
	public boolean hasNext() {
		return pageable.getPageNumber() < getTotalPages() - 1;
	}

	/**
	 * Returns whether there is a previous page.
	 *
	 * @return true if previous page exists
	 */
	public boolean hasPrevious() {
		return pageable.getPageNumber() > 0;
	}

	/**
	 * Returns whether this page is the first page.
	 *
	 * @return true if first page
	 */
	public boolean isFirst() {
		return !hasPrevious();
	}

	/**
	 * Returns whether this page is the last page.
	 *
	 * @return true if last page
	 */
	public boolean isLast() {
		return !hasNext();
	}

	/**
	 * Returns the number of elements on this page.
	 *
	 * @return number of elements
	 */
	public int getNumberOfElements() {
		return content.size();
	}

	/**
	 * Returns whether this page has content.
	 *
	 * @return true if has content
	 */
	public boolean hasContent() {
		return !content.isEmpty();
	}
}
