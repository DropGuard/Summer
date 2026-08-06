package com.github.dropguard.summer.aot;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import java.util.List;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;

/**
 * Emits inline {@code RowMapper} lambda registrations for {@code @RowModel} records, registered
 * directly on the {@code JdbcTemplate} singleton.
 *
 * <p>Split out of {@link WireMethodGenerator}: JDBC row-mapper codegen is a separate concern from
 * DI wire-body generation, and keeping it isolated lets non-JDBC AOT builds avoid touching
 * data-jdbc classes entirely (see {@link #ROW_MODEL_DOT}).
 */
@Internal
public final class RowMapperEmitter {

    private static final ClassName JDBC_TEMPLATE =
            ClassName.get("com.github.dropguard.summer.data.jdbc", "JdbcTemplate");

    // Lazy gate for JDBC row-mapper codegen. The @RowModel usage lookup is pure Jandex (no
    // data-jdbc classes touched), so non-JDBC apps never load RowMapperFactory or its transitive
    // deps (HikariCP, ...) during AOT codegen even though summer-data-jdbc stays a compile
    // dependency of this module — the gate keeps that dependency inert for apps without JDBC.
    private static final DotName ROW_MODEL_DOT =
            DotName.createSimple("com.github.dropguard.summer.data.jdbc.annotation.RowModel");

    private final IndexView index;

    public RowMapperEmitter(IndexView index) {
        this.index = index;
    }

    /**
     * Emits inline {@code RowMapper} lambda registrations for all {@code @RowModel} records in the
     * index. Mappers are registered directly on the {@code JdbcTemplate} singleton via {@code
     * registerMapper()}.
     *
     * <p>Gated by a pure-Jandex {@link #ROW_MODEL_DOT} lookup: when the index holds no
     * {@code @RowModel} classes the method returns without touching any data-jdbc type, so non-JDBC
     * AOT builds never load {@code RowMapperFactory} (see {@link #ROW_MODEL_DOT}).
     *
     * @param wire the wire method builder to append to
     * @param activeClassNames optional filter: only emit mappers for these class names
     * @param sortedBeans resolved bean definitions (unused directly; kept for signature parity with
     *     the wire-generation contract)
     */
    public void emitRowMapperRegistrations(
            MethodSpec.Builder wire,
            java.util.Set<String> activeClassNames,
            List<BeanDefinition> sortedBeans) {

        if (index == null) {
            return;
        }

        // Jandex-only gate (data-jdbc-free): skip without touching RowMapperFactory unless
        // @RowModel records actually exist in the index. scanJandex() is a real data-jdbc type
        // dependency of this module, so this guard keeps non-JDBC AOT builds from loading
        // data-jdbc classes at all.
        if (index.getAnnotations(ROW_MODEL_DOT).isEmpty()) {
            return;
        }

        List<com.github.dropguard.summer.data.jdbc.RowModelMeta> metas =
                com.github.dropguard.summer.data.jdbc.RowMapperFactory.scanJandex(index);

        if (activeClassNames != null) {
            metas =
                    metas.stream()
                            .filter(m -> activeClassNames.contains(m.modelClassName()))
                            .toList();
        }

        if (metas.isEmpty()) {
            return;
        }

        wire.addCode("\n");
        wire.addComment("Register @RowModel mappers with JdbcTemplate");
        wire.addStatement(
                "$T _jt = ($T) builder.peek($T.class)",
                JDBC_TEMPLATE,
                JDBC_TEMPLATE,
                JDBC_TEMPLATE);
        wire.beginControlFlow("if (_jt != null)");

        for (var meta : metas) {
            // scanJandex() already validates field types (fail-fast on both engines),
            // so no separate assertion is needed here.

            ClassName modelClass = AotTypeNames.safeClassName(meta.modelClassName());
            var mapperVar = meta.simpleName().toLowerCase(java.util.Locale.ROOT) + "Mapper";

            wire.addCode("\n");
            wire.addComment(meta.simpleName() + " RowMapper");
            wire.addStatement(
                    "$T<$T> $N = ($N, $N) -> {",
                    ClassName.get("com.github.dropguard.summer.data.jdbc", "RowMapper"),
                    modelClass,
                    mapperVar,
                    "rs",
                    "rowNum");
            for (var field : meta.fields()) {
                String colName =
                        com.github.dropguard.summer.data.jdbc.RowMapperFactory.camelToSnake(
                                field.name());
                CodeBlock readExpr = TypeReads.jdbcRead(colName, field.typeName());
                wire.addStatement(
                        "    $T $N = $L",
                        TypeReads.typeName(field.typeName()),
                        field.name(),
                        readExpr);
            }
            StringBuilder ctorArgs = new StringBuilder();
            for (int i = 0; i < meta.fields().size(); i++) {
                if (i > 0) ctorArgs.append(", ");
                ctorArgs.append(meta.fields().get(i).name());
            }
            wire.addStatement("    return new $T($L)", modelClass, ctorArgs.toString());
            wire.addStatement("}");
            wire.addStatement("_jt.registerMapper($T.class, $N)", modelClass, mapperVar);
        }

        wire.endControlFlow();
    }
}
