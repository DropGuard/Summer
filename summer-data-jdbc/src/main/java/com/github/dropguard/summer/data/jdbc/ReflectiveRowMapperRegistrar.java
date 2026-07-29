mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.data.jdbc;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.RuntimeDiMarker;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.annotation.ConditionalOnBean;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.data.jdbc.query.QueryBuilder;
mport com.github.dropguard.summer.core.Internal;
import org.jboss.jandex.IndexView;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * Registers reflective {@link RowMapper}s for every {@code @RowModel} record in the deployment
@Internal
mport com.github.dropguard.summer.core.Internal;
 * index — the Runtime engine's row-mapping path.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>This registrar is <b>Runtime-only</b>: the AOT engine emits inline mappers at build time
mport com.github.dropguard.summer.core.Internal;
 * instead (see {@code WireMethodGenerator}), so it must not also run reflective registration or the
mport com.github.dropguard.summer.core.Internal;
 * same mapper would be registered twice. The engine-agnostic structural metadata (table/column
mport com.github.dropguard.summer.core.Internal;
 * mapping for {@link QueryBuilder}) lives in {@link EntityMetadataRegistrar}, which runs on both
mport com.github.dropguard.summer.core.Internal;
 * engines.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
@ConditionalOnBean(RuntimeDiMarker.class)
mport com.github.dropguard.summer.core.Internal;
public final class ReflectiveRowMapperRegistrar {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private final JdbcTemplate jdbcTemplate;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public ReflectiveRowMapperRegistrar(JdbcTemplate jdbcTemplate, IndexView discoveryIndex) {
mport com.github.dropguard.summer.core.Internal;
        this.jdbcTemplate = jdbcTemplate;
mport com.github.dropguard.summer.core.Internal;
        for (RowModelMeta meta : RowMapperFactory.scanJandex(discoveryIndex)) {
mport com.github.dropguard.summer.core.Internal;
            jdbcTemplate.registerMapper(modelClass(meta), RowMapperFactory.createReflective(meta));
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static Class<?> modelClass(RowModelMeta meta) {
mport com.github.dropguard.summer.core.Internal;
        try {
mport com.github.dropguard.summer.core.Internal;
            return Class.forName(meta.modelClassName());
mport com.github.dropguard.summer.core.Internal;
        } catch (ClassNotFoundException e) {
mport com.github.dropguard.summer.core.Internal;
            throw new IllegalStateException(
mport com.github.dropguard.summer.core.Internal;
                    "Cannot load @RowModel class: " + meta.modelClassName(), e);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
