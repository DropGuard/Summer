package com.github.dropguard.summer.test;

import com.github.dropguard.summer.core.Internal;
import java.io.InputStream;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;

/**
 * Builds a Jandex {@link IndexView} for a narrow bean universe, used by
 * {@code @SummerTest(classes=...)} scoped tests.
 *
 * <p>The index carries exactly the given seed classes plus each seed's {@code package-info} — the
 * universe's membership. A {@link NarrowIndexes#info() second, separate index} carries the seeds'
 * {@code @Bean} methods' return types (a single level, no recursion): their ClassInfo must be
 * visible to lookups — the AOT engine is reflection-free, so the index is the only source of a
 * product's interfaces (the {@code @ConfigMapping} override dedup and the interface-keyed
 * registration both depend on them) — but they are NOT universe members, so discovery never
 * registers a {@code @Component}-annotated product class on top of the {@code @Bean} product.
 * Nested producers are not recursed: a {@code @Bean} product that is itself a configuration is
 * exotic (configuration composition goes through the component scan or {@code @Import}), and the
 * caller can still seed such classes explicitly.
 *
 * <p>The result is a self-contained index that the DI engines discover and wire as the whole
 * universe for that test — so a test listing {@code CycleNodeA, CycleNodeB} sees exactly those
 * classes and nothing else (which is what makes a broken graph fail assembly as the test promises).
 */
@Internal
public final class NarrowIndexBuilder {

    /**
     * The narrow universe's two index views: {@link #main()} carries exactly the seed classes (the
     * universe's membership — discovery iterates it), {@link #info()} carries the seeds'
     * {@code @Bean} return types (the produced products' classes — needed for their interface info
     * and the override dedup, but NOT universe members: the discovery must not register them as
     * components).
     */
    public record NarrowIndexes(IndexView main, IndexView info) {}

    private NarrowIndexBuilder() {}

    public static NarrowIndexes build(Class<?>... seeds) {
        Indexer indexer = new Indexer();
        Indexer infoIndexer = new Indexer();
        for (Class<?> seed : seeds) {
            indexClass(seed, indexer);
            indexPackageInfo(seed, indexer);
            indexBeanReturnTypes(seed, infoIndexer);
        }
        return new NarrowIndexes(indexer.complete(), infoIndexer.complete());
    }

    /**
     * Single-level info extraction: the seeds' {@code @Bean} methods' return types (the produced
     * products' classes) are indexed into the SEPARATE info index — their ClassInfo (interfaces
     * included) must be visible to the AOT engine (reflection-free, index-only) without making them
     * universe members (discovery must not register a @Component-annotated product class as a
     * component on top of the @Bean product). One level only; a product's own {@code @Bean} methods
     * are not recursed (nested producers are exotic; seed them explicitly if needed).
     */
    private static void indexBeanReturnTypes(Class<?> seed, Indexer indexer) {
        for (java.lang.reflect.Method m : seed.getDeclaredMethods()) {
            if (m.isAnnotationPresent(com.github.dropguard.summer.core.annotation.Bean.class)) {
                Class<?> rt = m.getReturnType();
                if (rt != void.class && !rt.isPrimitive()) {
                    indexClass(rt, indexer);
                }
            }
        }
    }

    private static void indexClass(Class<?> clazz, Indexer indexer) {
        String resource = "/" + clazz.getName().replace('.', '/') + ".class";
        try (InputStream is = clazz.getResourceAsStream(resource)) {
            if (is != null) {
                indexer.index(is);
            }
        } catch (Exception ignored) {
            // Skip classes we cannot read from the classpath (e.g. JDK internals);
            // they are never DI beans and need not be in the index.
        }
    }

    private static void indexPackageInfo(Class<?> clazz, Indexer indexer) {
        String pkg = clazz.getPackageName();
        if (pkg.isEmpty()) {
            return;
        }
        String resource = "/" + pkg.replace('.', '/') + "/package-info.class";
        try (InputStream is = clazz.getResourceAsStream(resource)) {
            if (is != null) {
                indexer.index(is);
            }
        } catch (Exception ignored) {
            // No package-info: Quarkus tolerates its absence, so do we.
        }
    }
}
