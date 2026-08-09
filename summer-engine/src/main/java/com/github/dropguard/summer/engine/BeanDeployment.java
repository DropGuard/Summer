package com.github.dropguard.summer.engine;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.IndexView;

/**
 * The unit of a single DI container build: the set of bean archives a container is assembled from.
 *
 * <p>This is the Quarkus {@code BeanDeployment} abstraction, copied faithfully. In Quarkus the
 * deployment holds two independently-sourced indexes: {@code getBeanArchiveIndex()} (the
 * application archives, built from {@code META-INF/jandex.idx}) and {@code getApplicationIndex()}
 * (extra types needed only for type-safe resolution). The two are <b>never</b> collapsed into one
 * flat index at load time; discovery consults them as distinct slices.
 *
 * <p>Summer mirrors that with two <b>disjoint pipelines</b>:
 *
 * <ul>
 *   <li><b>Production / AOT pipeline</b> — reads {@code jandex.idx} only ({@link
 *       #productionIndex()}). It never touches a test class. This is the boundary that keeps a
 *       generated AOT container free of test beans.
 *   <li><b>Test container pipeline</b> — the runtime reads {@code jandex.idx} for the application
 *       archives, and builds the <em>test</em> slice on demand by indexing exactly the running test
 *       class's {@code test-classes} directory via {@code
 *       com.github.dropguard.summer.test.TestClassIndexer} (Quarkus' {@code
 *       TestClassIndexer.indexTestClasses} model). There is no pre-baked {@code jandex-test.idx}
 *       and no bulk scan of every module's test classes. A narrow {@code @SummerTest(classes=...)}
 *       container replaces the test slice with a throwaway index built directly from the seed
 *       {@code .class} bytes ({@link #forNarrow(IndexView, IndexView)}), exactly like Quarkus'
 *       {@code ArcTestContainer.index(beanClasses)}.
 * </ul>
 *
 * <p>The two pipelines are disjoint by construction: the production loader and the test loader come
 * from different sources, and a negative (sad-path) fixture — which lives in its own module — is
 * simply not on the path of any application's {@code test-classes} directory, so it cannot enter a
 * whole-universe container. No path glob, no allow-list, no post-hoc filtering of a merged view —
 * the isolation lives in which archives each pipeline collects.
 *
 * <p>An <b>archive</b> is the immutable unit of discovery: one jar (or one build output directory)
 * is one archive, named after the artifact it ships in. Archives are isolated for
 * {@code @ConditionalOnBean} visibility — a bean's condition is satisfied only by another bean in
 * the <em>same</em> archive (see {@link #archiveOf(String)}). Bean <em>injection</em> remains
 * global; only the condition-evaluation boundary is archive-scoped.
 *
 * <p>Lives in the engine module (not summer-core) so the foundation layer stays free of Jandex —
 * the deployment's index slices are engine machinery, not contract.
 */
@Internal
public final class BeanDeployment {

    private final IndexView productionIndex;
    private final Map<String, String> classToArchive;
    private final Set<String> allTypeNames;
    private final Map<String, IndexView> archiveIndexes;

    /**
     * The merged discovery view: the archives' indexes plus (narrow only) the products' info index
     * — built once at construction.
     */
    private final IndexView discoveryIndex;

    /**
     * AOT codegen: unique cache key for the generated container (must encode profile overrides and
     * mocked types). The runtime engine ignores it — it is set only when a caller attaches AOT
     * codegen parameters via {@link #withCodegen(String, String)}.
     */
    private String cacheKey;

    /**
     * AOT codegen: generated container class name (without package). The runtime engine ignores it.
     */
    private String containerClassName;

    /**
     * Engine-provided (synthetic) beans — instances the engines inject rather than discover by
     * scanning (e.g. the discovery {@link IndexView}, the {@code RuntimeDiMarker}). This is the
     * blueprint's declaration of what to merge into the candidate set; the actual merge happens in
     * {@code Discovery} (its returned {@code beansView}). Quarkus models the same concept as {@code
     * BeanDeployment.syntheticBeans}, kept here as a pure blueprint — the merged candidate list is
     * Discovery's output, not held back here.
     */
    private final List<BeanDefinition> syntheticBeans = new ArrayList<>();

    private BeanDeployment(
            IndexView productionIndex,
            Map<String, String> classToArchive,
            Map<String, IndexView> archiveIndexes) {
        this.productionIndex = productionIndex;
        this.classToArchive = Collections.unmodifiableMap(classToArchive);
        this.allTypeNames = Collections.unmodifiableSet(classToArchive.keySet());
        this.archiveIndexes = Collections.unmodifiableMap(archiveIndexes);
        this.discoveryIndex = CompositeIndex.create(new ArrayList<>(archiveIndexes.values()));
        registerEngineSyntheticBeans();
    }

    /** Narrow deployment: no production slice, discovery sees only the seed closure. */
    private BeanDeployment(
            Map<String, String> classToArchive,
            Map<String, IndexView> archiveIndexes,
            IndexView narrowInfo) {
        this.productionIndex = null;
        this.classToArchive = Collections.unmodifiableMap(classToArchive);
        this.allTypeNames = Collections.unmodifiableSet(classToArchive.keySet());
        this.archiveIndexes = Collections.unmodifiableMap(archiveIndexes);
        // The products' info index (narrow only) is merged here and discarded — it is lookup
        // material (interfaces, override dedup), never a universe member, so it must not outlive
        // the deployment as a field.
        List<IndexView> all = new ArrayList<>(archiveIndexes.values());
        if (narrowInfo != null) {
            all.add(narrowInfo);
        }
        this.discoveryIndex = CompositeIndex.create(all);
        registerEngineSyntheticBeans();
    }

    /**
     * Declares engine-provided (synthetic) beans shared by every engine. The discovery {@link
     * IndexView} is one — it stays on the blueprint so BOTH engines' resolvers can satisfy
     * {@code @Bean} method parameters that take an {@code IndexView} (e.g. data-jdbc's registrar
     * methods).
     *
     * <p>The AOT expression is deliberately {@code null}: the AOT wire skips null-expression
     * synthetics, so the generated container never materializes the index at boot. That was the
     * only place the engine-neutral layer named a summer-runtime class, and the AOT-path consumer
     * (data-jdbc's {@code EntityMetadataRegistrar}) receives its {@code @RowModel} metadata baked
     * at codegen time (E1) instead — a reconstructed index at boot was production-only and diverged
     * from Runtime's test-aware discovery view. The Runtime engine materializes it from {@link
     * #discoveryIndex()} via the synthetic instance.
     */
    private void registerEngineSyntheticBeans() {
        addSyntheticBean(IndexView.class, discoveryIndex(), null);
    }

    /**
     * Builds a whole-universe test deployment: the application archives plus the current test
     * class's {@code test-classes} archive (indexed on demand by {@code
     * com.github.dropguard.summer.test.TestClassIndexer}, supplied inside {@code archiveIndexes}
     * under the {@code "test"} key). The two slices stay separate as distinct archives; {@link
     * #discoveryIndex()} merges them on demand for discovery.
     */
    public static BeanDeployment forTestUniverse(
            IndexView productionIndex,
            Map<String, String> classToArchive,
            Map<String, IndexView> archiveIndexes) {
        return new BeanDeployment(productionIndex, classToArchive, archiveIndexes);
    }

    /**
     * Builds a narrow (scoped) test deployment: the main index's classes are the universe (iterated
     * by discovery — the seed {@code .class} bytes, Quarkus {@code
     * ArcTestContainer.index(beanClasses)} shape); the info index's classes are lookup-only (the
     * {@code @Bean} products' interfaces and the override dedup) and excluded from the universe
     * membership.
     */
    public static BeanDeployment forNarrow(IndexView narrowIndex, IndexView narrowInfo) {
        Map<String, String> classToArchive = new java.util.HashMap<>();
        Map<String, IndexView> archiveIndexes = new java.util.HashMap<>();
        for (String typeName :
                narrowIndex.getKnownClasses().stream().map(Object::toString).toList()) {
            classToArchive.put(typeName, "narrow");
        }
        archiveIndexes.put("narrow", narrowIndex);
        return new BeanDeployment(classToArchive, archiveIndexes, narrowInfo);
    }

    /**
     * Builds a production deployment: the application archives only, no test slice. Used by runtime
     * startup and AOT code generation — a test class can never enter this deployment.
     */
    public static BeanDeployment forProduction(
            IndexView productionIndex,
            Map<String, String> classToArchive,
            Map<String, IndexView> archiveIndexes) {
        return new BeanDeployment(productionIndex, classToArchive, archiveIndexes);
    }

    /**
     * Builds a production deployment from a single merged index, treating it as one archive named
     * {@code "production"}. Convenience overload mirroring {@link #forNarrow(IndexView, IndexView)}
     * so callers don't have to build the archive maps by hand — the whole index is the universe, as
     * production expects.
     */
    public static BeanDeployment forProduction(IndexView productionIndex) {
        Map<String, String> classToArchive = new java.util.HashMap<>();
        Map<String, IndexView> archiveIndexes = new java.util.HashMap<>();
        for (String typeName :
                productionIndex.getKnownClasses().stream().map(Object::toString).toList()) {
            classToArchive.put(typeName, "production");
        }
        archiveIndexes.put("production", productionIndex);
        return new BeanDeployment(productionIndex, classToArchive, archiveIndexes);
    }

    /** Production archives only ({@code jandex.idx}). Never test classes. */
    public IndexView productionIndex() {
        return productionIndex;
    }

    /**
     * The merged discovery view: every raw per-archive index (production and test) composed into
     * one composite. This is the single point where the two slices combine, and it happens at
     * deployment-assembly time, never by collapsing archives at load time. The merge is flat (every
     * archive as one element) so {@code getClassByName} / {@code getKnownClasses} keep the
     * semantics discovery and bean-enrichment rely on.
     */
    public IndexView discoveryIndex() {
        return discoveryIndex;
    }

    /** All archive names. */
    public Set<String> archives() {
        return archiveIndexes.keySet();
    }

    /** All indexed type names, cached from the class-to-archive map. */
    public Set<String> allTypeNames() {
        return allTypeNames;
    }

    /**
     * Returns the archive name a class belongs to, or {@code null} if the class is not indexed.
     * This is the boundary key for {@code @ConditionalOnBean} visibility: a condition is satisfied
     * only by beans in the same archive.
     */
    public String archiveOf(String className) {
        return classToArchive.get(className);
    }

    /** The raw {@link IndexView} for a single archive. */
    public IndexView archiveIndex(String archiveName) {
        return archiveIndexes.get(archiveName);
    }

    /**
     * Registers an engine-provided (synthetic) bean to be merged into the candidate set by {@code
     * Discovery}. The instance is stored directly on the resulting {@link BeanDefinition} ({@code
     * syntheticInstance}) so the runtime engine registers it without re-instantiating. {@code
     * aotExpression} is the Java source the AOT engine emits to obtain the same instance at build
     * time — supplied here at the definition site so the code generator never needs to know how
     * each synthetic type is constructed.
     */
    public void addSyntheticBean(Class<?> type, Object instance, String aotExpression) {
        // Fail fast on duplicate type: a deployment is a pure blueprint with exactly one
        // definition per type — silently dropping a re-declaration would mask a wiring mistake.
        // (Each container build uses a fresh deployment, so no legitimate path re-adds a type.)
        for (BeanDefinition existing : syntheticBeans) {
            if (existing.qualifiedName.equals(type.getName())) {
                throw new IllegalStateException(
                        "Duplicate synthetic bean declaration for " + type.getName());
            }
        }
        BeanDefinition bd = new BeanDefinition(type.getName(), type.getSimpleName());
        bd.syntheticInstance = instance;
        bd.aotInstanceExpression = aotExpression;
        syntheticBeans.add(bd);
    }

    /** Engine-provided (synthetic) beans declared for this deployment. */
    public List<BeanDefinition> syntheticBeans() {
        return syntheticBeans;
    }

    /**
     * Attaches AOT codegen parameters (cache key + generated container class name) to this
     * deployment. Kept off the shared {@code ContainerEngine.build} SPI signature — they are
     * AOT-specific; the runtime engine ignores them. Returns this instance for chaining.
     */
    public BeanDeployment withCodegen(String cacheKey, String containerClassName) {
        this.cacheKey = cacheKey;
        this.containerClassName = containerClassName;
        return this;
    }

    /** AOT codegen cache key, or {@code null} when not attached. */
    public String cacheKey() {
        return cacheKey;
    }

    /** AOT codegen container class name (without package), or {@code null} when not attached. */
    public String containerClassName() {
        return containerClassName;
    }
}
