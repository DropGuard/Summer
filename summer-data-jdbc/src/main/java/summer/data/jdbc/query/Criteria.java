package summer.data.jdbc.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A structured query condition. Unlike raw SQL strings, a {@code Criteria}
 * never embeds a value into the SQL text — values are collected as bind
 * parameters and emitted as {@code ?} placeholders by the owning
 * {@link QueryBuilder}, so they are always passed through
 * {@code PreparedStatement} (no value-level injection).
 *
 * <p>
 * Column names are validated by {@link QueryBuilder} against the entity's known
 * columns before a {@code Criteria} is rendered, so a column string can never
 * reach generated SQL either.
 * </p>
 */
public sealed interface Criteria permits Criteria.Eq, Criteria.Comparison, Criteria.Like, Criteria.IsNull, Criteria.Composite {

	/**
	 * The column names this condition references — used for whitelist validation.
	 */
	Set<String> columns();

	/**
	 * Compiles this condition into a SQL fragment plus its ordered bind parameters.
	 */
	SqlFragment render();

	/** {@code column = ?} */
	record Eq(String column, Object value) implements Criteria {
		@Override
		public Set<String> columns() {
			return Set.of(column);
		}

		@Override
		public SqlFragment render() {
			return new SqlFragment(column + " = ?", List.of(value));
		}
	}

	/** {@code column <op> ?} for the ordered comparisons. */
	record Comparison(String column, String operator, Object value) implements Criteria {
		@Override
		public Set<String> columns() {
			return Set.of(column);
		}

		@Override
		public SqlFragment render() {
			return new SqlFragment(column + " " + operator + " ?", List.of(value));
		}
	}

	/**
	 * {@code column LIKE ?} (caller is responsible for {@code %} wildcards in the
	 * value).
	 */
	record Like(String column, String value) implements Criteria {
		@Override
		public Set<String> columns() {
			return Set.of(column);
		}

		@Override
		public SqlFragment render() {
			return new SqlFragment(column + " LIKE ?", List.of(value));
		}
	}

	/** {@code column IS NULL}. */
	record IsNull(String column) implements Criteria {
		@Override
		public Set<String> columns() {
			return Set.of(column);
		}

		@Override
		public SqlFragment render() {
			return new SqlFragment(column + " IS NULL", List.of());
		}
	}

	/** {@code (a AND b AND ...)} or {@code (a OR b OR ...)}. */
	record Composite(String operator, List<Criteria> parts) implements Criteria {
		@Override
		public Set<String> columns() {
			Set<String> all = new java.util.LinkedHashSet<>();
			for (Criteria part : parts) {
				all.addAll(part.columns());
			}
			return all;
		}

		@Override
		public SqlFragment render() {
			List<String> fragments = new ArrayList<>();
			List<Object> params = new ArrayList<>();
			for (Criteria part : parts) {
				SqlFragment f = part.render();
				fragments.add(f.fragment());
				params.addAll(f.params());
			}
			return new SqlFragment("(" + String.join(" " + operator + " ", fragments) + ")", params);
		}
	}

	/** A rendered SQL fragment and its ordered bind parameters. */
	record SqlFragment(String fragment, List<Object> params) {
	}
}
