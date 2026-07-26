package com.github.dropguard.summer.data.jdbc;

import com.github.dropguard.summer.data.jdbc.query.QueryBuilder;
import org.jboss.jandex.IndexView;

/**
 * Engine-agnostic registration of {@code @RowModel} metadata used by
 * {@link QueryBuilder} (table name, column whitelist). Runs on both the Runtime
 * and AOT engines so QueryBuilder works identically everywhere.
 *
 * <p>
 * This registrar owns only the structural metadata (table/column mapping)
 * needed for query construction — it does <em>not</em> register row mappers.
 * Mapper registration is engine-specific: the Runtime engine uses a reflective
 * {@link ReflectiveRowMapperRegistrar}, the AOT engine emits inline mappers at
 * build time. Splitting the two concerns means the AOT engine can skip
 * reflective mapping entirely (honouring its zero-reflection goal) without
 * losing QueryBuilder's metadata.
 * </p>
 *
 * <p>
 * Field-type validation happens inside
 * {@link RowMapperFactory#scanJandex(IndexView)} so an unsupported field type
 * fails fast on <em>both</em> engines, rather than only on the runtime path.
 * </p>
 */
public final class EntityMetadataRegistrar {

	private final EntityMetadataRegistry entityMetadataRegistry;

	public EntityMetadataRegistrar(IndexView discoveryIndex, EntityMetadataRegistry entityMetadataRegistry) {
		this.entityMetadataRegistry = entityMetadataRegistry;
		for (RowModelMeta meta : RowMapperFactory.scanJandex(discoveryIndex)) {
			entityMetadataRegistry.register(meta);
		}
	}
}
