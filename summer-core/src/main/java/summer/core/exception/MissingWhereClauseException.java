package summer.core.exception;

/**
 * Thrown when a {@code UPDATE} or {@code DELETE} is issued without a
 * {@code WHERE} clause.
 *
 * <p>
 * An unqualified mutation would touch every row in the table, so QueryBuilder
 * rejects it as a usage error rather than letting it reach the database. This
 * is a caller-side mistake (forgot {@code .where(...)}), distinct from
 * infrastructure failures captured by {@link DataAccessException}.
 * </p>
 */
public class MissingWhereClauseException extends DataAccessException {

	public MissingWhereClauseException(String message) {
		super(message);
	}
}
