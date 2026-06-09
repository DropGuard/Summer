package summer.web;
import java.util.List;

/**
 * Abstract interface for pagination information. Follows the Spring Data JPA
 * Pageable interface design.
 *
 * <p>
 * Usage in controllers:
 * </p>
 * 
 * <pre>
 * &#64;Get("/articles")
 * public void listArticles(HttpContext ctx, Pageable pageable) {
 * 	int offset = pageable.getPageNumber() * pageable.getPageSize();
 * 	List&lt;Article&gt; articles = service.findAll(offset, pageable.getPageSize());
 * }
 * </pre>
 *
 * @see PageRequest
 */
public interface Pageable {

	/**
	 * Returns the zero-based page index.
	 *
	 * @return the page number
	 */
	int getPageNumber();

	/**
	 * Returns the size of the page.
	 *
	 * @return the page size
	 */
	int getPageSize();

	/**
	 * Returns the sorting parameters.
	 *
	 * @return the sort specification
	 */
	Sort getSort();

	/**
	 * Returns whether pagination is enabled. If disabled, all results are returned.
	 *
	 * @return true if pagination is active
	 */
	default boolean isPaged() {
		return true;
	}

	/**
	 * Returns an unpaged version of this Pageable.
	 *
	 * @return an unpaged Pageable
	 */
	default Pageable unpaged() {
		return Pageable.UNPAGED;
	}
	/**
	 * Paginates a list of items using this pageable's page number and size.
	 *
	 * <p>
	 * Example usage:
	 * </p>
	 *
	 * <pre>
	 * Page&lt;Article&gt; page = pageable.paginate(allArticles);
	 * ctx.json(HttpStatus.OK, new ArticlesResponse(page.content(), page.totalElements()));
	 * </pre>
	 *
	 * @param items
	 *            the full list of items to paginate
	 * @return a Page containing the paginated subset and metadata
	 */
	default <T> Page<T> paginate(List<T> items) {
		int offset = getPageNumber() * getPageSize();
		int start = Math.min(offset, items.size());
		int end = Math.min(start + getPageSize(), items.size());
		return new Page<>(items.subList(start, end), items.size(), this);
	}

	/**
	 * Singleton instance for unpaged requests.
	 */
	Pageable UNPAGED = new Pageable() {
		@Override
		public int getPageNumber() {
			return 0;
		}

		@Override
		public int getPageSize() {
			return Integer.MAX_VALUE;
		}

		@Override
		public Sort getSort() {
			return Sort.unsorted();
		}

		@Override
		public boolean isPaged() {
			return false;
		}

		@Override
		public Pageable unpaged() {
			return this;
		}
	};
}
