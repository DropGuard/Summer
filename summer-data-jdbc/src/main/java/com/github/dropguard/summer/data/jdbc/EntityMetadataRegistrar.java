package com.github.dropguard.summer.data.jdbc;

import com.github.dropguard.summer.data.jdbc.query.QueryBuilder;
import java.util.List;
import org.jboss.jandex.IndexView;

/**
 * Engine-agnostic registration of {@code @RowModel} metadata used by {@link QueryBuilder} (table
 * name, column whitelist). Runs on both the Runtime and AOT engines so QueryBuilder works
 * identically everywhere.
 *
 * <p>This registrar owns only the structural metadata (table/column mapping) needed for query
 * construction — it does <em>not</em> register row mappers. Mapper registration is engine-specific:
 * the Runtime engine uses a reflective {@link ReflectiveRowMapperRegistrar}, the AOT engine emits
 * inline mappers at build time. Splitting the two concerns means the AOT engine can skip reflective
 * mapping entirely (honouring its zero-reflection goal) without losing QueryBuilder's metadata.
 *
 * <p>Field-type validation happens inside {@link RowMapperFactory#scanJandex(IndexView)} so an
 * unsupported field type fails fast on <em>both</em> engines, rather than only on the runtime path.
 */
public final class EntityMetadataRegistrar {

    private final EntityMetadataRegistry entityMetadataRegistry;

    /**
     * Runtime engine path: scans the deployment's discovery {@link IndexView} at container build
     * time (the runtime view is test-aware, and reading metadata from the index at runtime mirrors
     * what Spring does with its metadata readers). The AOT engine instead uses {@link
     * #fromMetas(List, EntityMetadataRegistry)} with metadata computed at code-generation time from
     * the same discovery view, so both engines see identical {@code @RowModel} sets — including
     * test-classes entities in test universes.
     */
    public EntityMetadataRegistrar(
            IndexView discoveryIndex, EntityMetadataRegistry entityMetadataRegistry) {
        this(entityMetadataRegistry);
        for (RowModelMeta meta : RowMapperFactory.scanJandex(discoveryIndex)) {
            entityMetadataRegistry.register(meta);
        }
    }

    /**
     * AOT engine path: registers pre-computed {@code @RowModel} metadata (generated from the
     * deployment's discovery index at codegen time — test-aware). A static factory rather than a
     * second public constructor, so container discovery's single-public-constructor contract holds
     * for the {@code @Bean} product.
     */
    public static EntityMetadataRegistrar fromMetas(
            List<RowModelMeta> metas, EntityMetadataRegistry entityMetadataRegistry) {
        EntityMetadataRegistrar registrar = new EntityMetadataRegistrar(entityMetadataRegistry);
        for (RowModelMeta meta : metas) {
            entityMetadataRegistry.register(meta);
        }
        return registrar;
    }

    private EntityMetadataRegistrar(EntityMetadataRegistry entityMetadataRegistry) {
        this.entityMetadataRegistry = entityMetadataRegistry;
    }
}
