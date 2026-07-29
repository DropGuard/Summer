mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.data.jdbc;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.data.jdbc.query.QueryBuilder;
mport com.github.dropguard.summer.core.Internal;
import org.jboss.jandex.IndexView;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * Engine-agnostic registration of {@code @RowModel} metadata used by {@link QueryBuilder} (table
mport com.github.dropguard.summer.core.Internal;
 * name, column whitelist). Runs on both the Runtime and AOT engines so QueryBuilder works
mport com.github.dropguard.summer.core.Internal;
 * identically everywhere.
mport com.github.dropguard.summer.core.Internal;
@Internal
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>This registrar owns only the structural metadata (table/column mapping) needed for query
mport com.github.dropguard.summer.core.Internal;
 * construction — it does <em>not</em> register row mappers. Mapper registration is engine-specific:
mport com.github.dropguard.summer.core.Internal;
 * the Runtime engine uses a reflective {@link ReflectiveRowMapperRegistrar}, the AOT engine emits
mport com.github.dropguard.summer.core.Internal;
 * inline mappers at build time. Splitting the two concerns means the AOT engine can skip reflective
mport com.github.dropguard.summer.core.Internal;
 * mapping entirely (honouring its zero-reflection goal) without losing QueryBuilder's metadata.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>Field-type validation happens inside {@link RowMapperFactory#scanJandex(IndexView)} so an
mport com.github.dropguard.summer.core.Internal;
 * unsupported field type fails fast on <em>both</em> engines, rather than only on the runtime path.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public final class EntityMetadataRegistrar {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private final EntityMetadataRegistry entityMetadataRegistry;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public EntityMetadataRegistrar(
mport com.github.dropguard.summer.core.Internal;
            IndexView discoveryIndex, EntityMetadataRegistry entityMetadataRegistry) {
mport com.github.dropguard.summer.core.Internal;
        this.entityMetadataRegistry = entityMetadataRegistry;
mport com.github.dropguard.summer.core.Internal;
        for (RowModelMeta meta : RowMapperFactory.scanJandex(discoveryIndex)) {
mport com.github.dropguard.summer.core.Internal;
            entityMetadataRegistry.register(meta);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
