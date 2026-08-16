package com.github.dropguard.summer.engine.spi;

import com.github.dropguard.summer.core.Internal;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Loads every {@link AotProductConstructor} on the classpath (via {@link ServiceLoader}) into a
 * product-type-keyed map consulted by the AOT wire generator. The engine counterpart of {@code
 * RouteRegistrarLoader}: modules opt their {@code @Bean} products into custom AOT assembly — a
 * construction override (e.g. data-jdbc's {@code EntityMetadataRegistrar}) and/or post-construction
 * statements (e.g. data-jdbc's {@code JdbcTemplate}). The wire generator resolves a provider along
 * the product's supertype chain, so a provider registered for a base type also assembles subclass
 * products. Lives in {@code engine.spi} — the one place outside {@code core.spi} the framework
 * permits ServiceLoader discovery (alongside {@link ContainerEngines}).
 */
@Internal
public final class AotProductConstructors {

    private static final Map<String, AotProductConstructor> BY_PRODUCT = load();

    private AotProductConstructors() {}

    /**
     * The custom constructor for a product type, or {@code null} when generic construction applies.
     */
    public static AotProductConstructor forProduct(String productTypeName) {
        return BY_PRODUCT.get(productTypeName);
    }

    private static Map<String, AotProductConstructor> load() {
        Map<String, AotProductConstructor> map = new HashMap<>();
        for (AotProductConstructor provider : ServiceLoader.load(AotProductConstructor.class)) {
            AotProductConstructor previous = map.putIfAbsent(provider.productTypeName(), provider);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate AotProductConstructor for product type: "
                                + provider.productTypeName());
            }
        }
        return Map.copyOf(map);
    }
}
