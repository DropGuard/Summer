package com.github.dropguard.summer.data.jdbc.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A structured query condition. Unlike raw SQL strings, a {@code Criteria} never embeds a value
 * into the SQL text — values are collected as bind parameters and emitted as {@code ?} placeholders
 * by the owning {@link QueryBuilder}, so they are always passed through {@code PreparedStatement}
 * (no value-level injection).
 *
 * <p>Column names are validated by {@link QueryBuilder} against the entity's known columns before a
 * {@code Criteria} is rendered, so a column string can never reach generated SQL either.
 */
public sealed interface Criteria
        permits Criteria.Eq,
                Criteria.Comparison,
                Criteria.Like,
                Criteria.IsNull,
                Criteria.ColEq,
                Criteria.JoinPredicate,
                Criteria.ExistsPredicate,
                Criteria.Composite {

    /** The column names this condition references — used for whitelist validation. */
    Set<String> columns();

    /** Compiles this condition into a SQL fragment plus its ordered bind parameters. */
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

    /** {@code column LIKE ?} (caller is responsible for {@code %} wildcards in the value). */
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

    /**
     * {@code leftColumn = rightColumn} — a column-to-column equality, used for join / EXISTS {@code
     * ON} predicates where the right-hand side is another column, not a bind value. Both columns
     * are validated against metadata by the owning {@link QueryBuilder} (which understands table
     * aliases).
     */
    record ColEq(String leftColumn, String rightColumn) implements Criteria {
        @Override
        public Set<String> columns() {
            return Set.of(leftColumn, rightColumn);
        }

        @Override
        public SqlFragment render() {
            return new SqlFragment(leftColumn + " = " + rightColumn, List.of());
        }
    }

    /**
     * A {@code JOIN} predicate: contributes its {@code ON} clause to the FROM clause (rendered by
     * the owning {@link QueryBuilder}'s join pass) and its columns/params to validation and
     * binding. It implements {@link Criteria} so it can live uniformly in the predicate list
     * alongside {@link ExistsPredicate}.
     */
    record JoinPredicate(String alias, String tableName, Criteria on) implements Criteria {
        @Override
        public Set<String> columns() {
            return on.columns();
        }

        @Override
        public SqlFragment render() {
            // The ON clause is real SQL with its own bind values; returning the
            // underlying fragment (not a placeholder "1=1") keeps the owning
            // QueryBuilder's param collection aligned with the emitted `?`s.
            return on.render();
        }
    }

    /**
     * A {@code WHERE EXISTS} predicate: renders as {@code EXISTS (SELECT 1 FROM <alias> WHERE
     * <on>)}. Because it is a sub-query rather than a JOIN, matching rows are not multiplied, so
     * count and pagination stay correct for many-to-many relationships.
     */
    record ExistsPredicate(String alias, String tableName, Criteria on) implements Criteria {
        @Override
        public Set<String> columns() {
            return on.columns();
        }

        @Override
        public SqlFragment render() {
            SqlFragment onFragment = on.render();
            return new SqlFragment(
                    "EXISTS (SELECT 1 FROM "
                            + tableName
                            + " "
                            + alias
                            + " WHERE "
                            + onFragment.fragment()
                            + ")",
                    onFragment.params());
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
            return new SqlFragment(
                    "(" + String.join(" " + operator + " ", fragments) + ")", params);
        }
    }

    /** A rendered SQL fragment and its ordered bind parameters. */
    record SqlFragment(String fragment, List<Object> params) {}
}
