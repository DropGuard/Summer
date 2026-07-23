package summer.web;

/**
 * Built-in cursor-based pageable, the framework's out-of-the-box alternative to
 * {@link DefaultPageRequest} (offset paging). Carries an opaque {@code cursor}
 * and a {@code limit}; the cursor is typically a sequential id (e.g. a
 * snowflake) or any value the caller can present to resume from the previous
 * page.
 *
 * <p>
 * Like {@link DefaultPageRequest}, it implements {@link ScrollRequest} so the
 * framework recognises it as a pageable parameter and routes it to
 * {@link CursorPageResolver}. Demos that need cursor semantics different from
 * "cursor id + limit" define their own {@code ScrollRequest} subtype and a
 * dedicated resolver — the built-in pair covers the common case only.
 * </p>
 */
public record CursorPageable(Long cursor, int limit) implements ScrollRequest {
}
