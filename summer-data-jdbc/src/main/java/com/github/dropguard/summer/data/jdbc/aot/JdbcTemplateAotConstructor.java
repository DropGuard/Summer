package com.github.dropguard.summer.data.jdbc.aot;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.github.dropguard.summer.data.jdbc.RowMapperFactory;
import com.github.dropguard.summer.data.jdbc.RowModelMeta;
import com.github.dropguard.summer.engine.spi.AotProductConstructor;
import java.util.List;
import org.jboss.jandex.IndexView;

/**
 * AOT assembly for {@link JdbcTemplate}: emits the {@code registerMapper} calls for every
 * {@code @RowModel} computed from the discovery index, as post-construction statements. This is the
 * generated counterpart of the runtime engine's {@code ReflectiveRowMapperRegistrar} — the same
 * assembly-time fill API, emitted by the build phase instead of a filler bean — and the container
 * seal phase then freezes the template, exactly as on the runtime engine.
 *
 * <p>Deliberately <em>not</em> a construction override: overriding the construction would replace
 * the user's {@code @Bean} producer body (a producer's own {@code registerMapper} setup would be
 * silently dropped) and would miss subclass products (the provider is resolved along the supertype
 * chain, so this fill applies to any {@code JdbcTemplate}-typed product). Post-construction
 * statements preserve the producer body and cover subclasses.
 *
 * <p>Registered via {@code
 * META-INF/services/com.github.dropguard.summer.engine.spi.AotProductConstructor}.
 */
@Internal
public final class JdbcTemplateAotConstructor implements AotProductConstructor {

    @Override
    public String productTypeName() {
        return JdbcTemplate.class.getName();
    }

    @Override
    public List<String> postConstruction(BeanDefinition bean, IndexView index) {
        return RowMapperFactory.scanJandex(index).stream()
                .map(JdbcTemplateAotConstructor::registerMapperStatement)
                .toList();
    }

    /**
     * Source statement (right-hand side of {@code <var>.registerMapper(...);}) for one model, e.g.
     * {@code registerMapper(pkg.User.class, (RowMapper<pkg.User>) (rs, rowNum) -> { java.lang.Long
     * id = rs.getObject("id", java.lang.Long.class); ...; return new pkg.User(id, ...); })}. The
     * explicit {@code RowMapper<X>} cast gives the lambda its target type — a bare lambda would be
     * ambiguous against {@code registerMapper(Class<?>, RowMapper<?>)}. The field read uses the
     * same {@code RowMapperFactory.resolveFieldType} contract as the reflective mapper (and the
     * former generated lambdas), so both engines read rows identically.
     */
    private static String registerMapperStatement(RowModelMeta meta) {
        StringBuilder sb =
                new StringBuilder("registerMapper(")
                        .append(meta.modelClassName())
                        .append(".class, (com.github.dropguard.summer.data.jdbc.RowMapper<")
                        .append(meta.modelClassName())
                        .append(">) (rs, rowNum) -> {\n");
        for (var field : meta.fields()) {
            String colName = RowMapperFactory.camelToSnake(field.name());
            String fieldType =
                    RowMapperFactory.resolveFieldType(field.typeName()).getCanonicalName();
            sb.append("    ")
                    .append(fieldType)
                    .append(" ")
                    .append(field.name())
                    .append(" = rs.getObject(\"")
                    .append(quote(colName))
                    .append("\", ")
                    .append(fieldType)
                    .append(".class);\n");
        }
        sb.append("    return new ").append(meta.modelClassName()).append("(");
        for (int j = 0; j < meta.fields().size(); j++) {
            if (j > 0) {
                sb.append(", ");
            }
            sb.append(meta.fields().get(j).name());
        }
        return sb.append(");\n})").toString();
    }

    /** Java string literal escaping (the expression is embedded verbatim into generated code). */
    private static String quote(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
