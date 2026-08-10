package com.github.dropguard.summer.data.jdbc.aot;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.bean.InjectionParameter;
import com.github.dropguard.summer.data.jdbc.EntityMetadataRegistrar;
import com.github.dropguard.summer.data.jdbc.EntityMetadataRegistry;
import com.github.dropguard.summer.data.jdbc.FieldMeta;
import com.github.dropguard.summer.data.jdbc.RowMapperFactory;
import com.github.dropguard.summer.data.jdbc.RowModelMeta;
import com.github.dropguard.summer.engine.spi.AotProductConstructor;
import java.util.List;
import org.jboss.jandex.IndexView;

/**
 * AOT construction for {@link EntityMetadataRegistrar}: bakes the {@code @RowModel} metadata
 * computed from the discovery index at codegen time.
 *
 * <p>The registrar's declared constructor takes an {@code IndexView}, which the generated container
 * deliberately never materializes (the AOT path is reflection-free and its codegen-time discovery
 * view is test-aware) — so this provider emits {@code EntityMetadataRegistrar.fromMetas( ...)} with
 * the metadata as a source literal, mirroring what the runtime engine's index-scanning constructor
 * produces. Registered via {@code META-INF/services/
 * com.github.dropguard.summer.engine.spi.AotProductConstructor}.
 */
@Internal
public final class EntityMetadataAotConstructor implements AotProductConstructor {

    @Override
    public String productTypeName() {
        return EntityMetadataRegistrar.class.getName();
    }

    @Override
    public String construction(BeanDefinition bean, IndexView index) {
        List<RowModelMeta> metas = RowMapperFactory.scanJandex(index);
        // The owning config declares (IndexView, EntityMetadataRegistry); resolve the registry
        // parameter by type so the expression never depends on constructor position.
        InjectionParameter registryParam =
                bean.parameters.stream()
                        .filter(p -> p.typeName().equals(EntityMetadataRegistry.class.getName()))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "EntityMetadataRegistrar product has no"
                                                        + " EntityMetadataRegistry parameter: "
                                                        + bean.qualifiedName));
        return EntityMetadataRegistrar.class.getName()
                + ".fromMetas("
                + metasLiteral(metas)
                + ", "
                + registryParam.resolved().get(0).variableName
                + ")";
    }

    /**
     * Source literal for a pre-computed {@code @RowModel} metadata list, e.g. {@code
     * java.util.List.of(new com...RowModelMeta("pkg.M", "pkg", "M", "t", java.util.List.of(new
     * com...FieldMeta("id", "java.lang.Long"))))}. Only the table name is user-supplied and needs
     * escaping; class/field names are Java identifiers by construction.
     */
    private static String metasLiteral(List<RowModelMeta> metas) {
        StringBuilder sb = new StringBuilder("java.util.List.of(");
        for (int i = 0; i < metas.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            RowModelMeta meta = metas.get(i);
            sb.append("new ")
                    .append(RowModelMeta.class.getName())
                    .append("(")
                    .append(quote(meta.modelClassName()))
                    .append(", ")
                    .append(quote(meta.packageName()))
                    .append(", ")
                    .append(quote(meta.simpleName()))
                    .append(", ")
                    .append(quote(meta.tableName()))
                    .append(", java.util.List.of(");
            for (int j = 0; j < meta.fields().size(); j++) {
                if (j > 0) {
                    sb.append(", ");
                }
                FieldMeta field = meta.fields().get(j);
                sb.append("new ")
                        .append(FieldMeta.class.getName())
                        .append("(")
                        .append(quote(field.name()))
                        .append(", ")
                        .append(quote(field.typeName()))
                        .append(")");
            }
            sb.append("))");
        }
        return sb.append(")").toString();
    }

    /** Java string literal escaping (the expression is embedded verbatim into generated code). */
    private static String quote(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
