mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.core.bean;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import java.util.ArrayList;
mport com.github.dropguard.summer.core.Internal;
import java.util.Collections;
mport com.github.dropguard.summer.core.Internal;
import java.util.List;
mport com.github.dropguard.summer.core.Internal;
import java.util.Map;
mport com.github.dropguard.summer.core.Internal;
import java.util.Set;
mport com.github.dropguard.summer.core.Internal;
import org.jboss.jandex.CompositeIndex;
mport com.github.dropguard.summer.core.Internal;
import org.jboss.jandex.IndexView;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * The unit of a single DI container build: the set of bean archives a container is assembled from.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>This is the Quarkus {@code BeanDeployment} abstraction, copied faithfully. In Quarkus the
mport com.github.dropguard.summer.core.Internal;
 * deployment holds two independently-sourced indexes: {@code getBeanArchiveIndex()} (the
mport com.github.dropguard.summer.core.Internal;
 * application archives, built from {@code META-INF/jandex.idx}) and {@code getApplicationIndex()}
mport com.github.dropguard.summer.core.Internal;
 * (extra types needed only for type-safe resolution). The two are <b>never</b> collapsed into one
mport com.github.dropguard.summer.core.Internal;
 * flat index at load time; discovery consults them as distinct slices.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>Summer mirrors that with two <b>disjoint pipelines</b>:
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <ul>
mport com.github.dropguard.summer.core.Internal;
 *   <li><b>Production / AOT pipeline</b> — reads {@code jandex.idx} only ({@link
mport com.github.dropguard.summer.core.Internal;
 *       #productionIndex()}). It never touches a test class. This is the boundary that keeps a
@Internal
mport com.github.dropguard.summer.core.Internal;
 *       generated AOT container free of test beans.
mport com.github.dropguard.summer.core.Internal;
 *   <li><b>Test container pipeline</b> — the runtime reads {@code jandex.idx} for the application
mport com.github.dropguard.summer.core.Internal;
 *       archives, and builds the <em>test</em> slice on demand by indexing exactly the running test
mport com.github.dropguard.summer.core.Internal;
 *       class's {@code test-classes} directory via {@code
mport com.github.dropguard.summer.core.Internal;
 *       com.github.dropguard.summer.runtime.TestClassIndexer} (Quarkus' {@code
mport com.github.dropguard.summer.core.Internal;
 *       TestClassIndexer.indexTestClasses} model). There is no pre-baked {@code jandex-test.idx}
mport com.github.dropguard.summer.core.Internal;
 *       and no bulk scan of every module's test classes. A narrow {@code @SummerTest(classes=...)}
mport com.github.dropguard.summer.core.Internal;
 *       container replaces the test slice with a throwaway index built directly from the seed
mport com.github.dropguard.summer.core.Internal;
 *       {@code .class} bytes ({@link #forNarrow(IndexView)}), exactly like Quarkus' {@code
mport com.github.dropguard.summer.core.Internal;
 *       ArcTestContainer.index(beanClasses)}.
mport com.github.dropguard.summer.core.Internal;
 * </ul>
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * The two pipelines are disjoint by construction: the production loader and the test loader come
mport com.github.dropguard.summer.core.Internal;
 * from different sources, and a negative (sad-path) fixture — which lives in its own module — is
mport com.github.dropguard.summer.core.Internal;
 * simply not on the path of any application's {@code test-classes} directory, so it cannot enter a
mport com.github.dropguard.summer.core.Internal;
 * whole-universe container. No path glob, no allow-list, no post-hoc filtering of a merged view —
mport com.github.dropguard.summer.core.Internal;
 * the isolation lives in which archives each pipeline collects.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>An <b>archive</b> is the immutable unit of discovery: one jar (or one build output directory)
mport com.github.dropguard.summer.core.Internal;
 * is one archive, named after the artifact it ships in. Archives are isolated for
mport com.github.dropguard.summer.core.Internal;
 * {@code @ConditionalOnBean} visibility — a bean's condition is satisfied only by another bean in
mport com.github.dropguard.summer.core.Internal;
 * the <em>same</em> archive (see {@link #archiveOf(String)}). Bean <em>injection</em> remains
mport com.github.dropguard.summer.core.Internal;
 * global; only the condition-evaluation boundary is archive-scoped.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public final class BeanDeployment {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private final IndexView productionIndex;
mport com.github.dropguard.summer.core.Internal;
    private final Map<String, String> classToArchive;
mport com.github.dropguard.summer.core.Internal;
    private final Set<String> allTypeNames;
mport com.github.dropguard.summer.core.Internal;
    private final Map<String, IndexView> archiveIndexes;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Engine-provided (synthetic) beans — instances the engines inject rather than discover by
mport com.github.dropguard.summer.core.Internal;
     * scanning (e.g. the discovery {@link IndexView}, the {@code RuntimeDiMarker}). This is the
mport com.github.dropguard.summer.core.Internal;
     * blueprint's declaration of what to merge into the candidate set; the actual merge happens in
mport com.github.dropguard.summer.core.Internal;
     * {@code Discovery} (its returned {@code beansView}). Quarkus models the same concept as {@code
mport com.github.dropguard.summer.core.Internal;
     * BeanDeployment.syntheticBeans}, kept here as a pure blueprint — the merged candidate list is
mport com.github.dropguard.summer.core.Internal;
     * Discovery's output, not held back here.
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    private final List<BeanDefinition> syntheticBeans = new ArrayList<>();
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private BeanDeployment(
mport com.github.dropguard.summer.core.Internal;
            IndexView productionIndex,
mport com.github.dropguard.summer.core.Internal;
            Map<String, String> classToArchive,
mport com.github.dropguard.summer.core.Internal;
            Map<String, IndexView> archiveIndexes) {
mport com.github.dropguard.summer.core.Internal;
        this.productionIndex = productionIndex;
mport com.github.dropguard.summer.core.Internal;
        this.classToArchive = Collections.unmodifiableMap(classToArchive);
mport com.github.dropguard.summer.core.Internal;
        this.allTypeNames = Collections.unmodifiableSet(classToArchive.keySet());
mport com.github.dropguard.summer.core.Internal;
        this.archiveIndexes = Collections.unmodifiableMap(archiveIndexes);
mport com.github.dropguard.summer.core.Internal;
        registerEngineSyntheticBeans();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /** Narrow deployment: no production slice, discovery sees only the seed closure. */
mport com.github.dropguard.summer.core.Internal;
    private BeanDeployment(
mport com.github.dropguard.summer.core.Internal;
            Map<String, String> classToArchive, Map<String, IndexView> archiveIndexes) {
mport com.github.dropguard.summer.core.Internal;
        this.productionIndex = null;
mport com.github.dropguard.summer.core.Internal;
        this.classToArchive = Collections.unmodifiableMap(classToArchive);
mport com.github.dropguard.summer.core.Internal;
        this.allTypeNames = Collections.unmodifiableSet(classToArchive.keySet());
mport com.github.dropguard.summer.core.Internal;
        this.archiveIndexes = Collections.unmodifiableMap(archiveIndexes);
mport com.github.dropguard.summer.core.Internal;
        registerEngineSyntheticBeans();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Declares engine-provided (synthetic) beans shared by every engine. The discovery {@link
mport com.github.dropguard.summer.core.Internal;
     * IndexView} is one — both Runtime and AOT need it (data-jdbc's EntityMetadataRegistrar depends
mport com.github.dropguard.summer.core.Internal;
     * on it). Registering it here, on the blueprint, means neither engine hand-registers it;
mport com.github.dropguard.summer.core.Internal;
     * Discovery folds it into the beansView. Engine-specific synthetic beans (e.g. RuntimeDiMarker)
mport com.github.dropguard.summer.core.Internal;
     * are added by the respective engine, not here.
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    private void registerEngineSyntheticBeans() {
mport com.github.dropguard.summer.core.Internal;
        addSyntheticBean(
mport com.github.dropguard.summer.core.Internal;
                IndexView.class,
mport com.github.dropguard.summer.core.Internal;
                discoveryIndex(),
mport com.github.dropguard.summer.core.Internal;
                "com.github.dropguard.summer.runtime.JandexIndexLoader.productionIndex().index()");
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Builds a whole-universe test deployment: the application archives plus the current test
mport com.github.dropguard.summer.core.Internal;
     * class's {@code test-classes} archive (indexed on demand by {@code
mport com.github.dropguard.summer.core.Internal;
     * com.github.dropguard.summer.runtime.TestClassIndexer}, supplied inside {@code archiveIndexes}
mport com.github.dropguard.summer.core.Internal;
     * under the {@code "test"} key). The two slices stay separate as distinct archives; {@link
mport com.github.dropguard.summer.core.Internal;
     * #discoveryIndex()} merges them on demand for discovery.
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public static BeanDeployment forTestUniverse(
mport com.github.dropguard.summer.core.Internal;
            IndexView productionIndex,
mport com.github.dropguard.summer.core.Internal;
            Map<String, String> classToArchive,
mport com.github.dropguard.summer.core.Internal;
            Map<String, IndexView> archiveIndexes) {
mport com.github.dropguard.summer.core.Internal;
        return new BeanDeployment(productionIndex, classToArchive, archiveIndexes);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Builds a narrow (scoped) test deployment: the test slice is a throwaway index built directly
mport com.github.dropguard.summer.core.Internal;
     * from the seed {@code .class} bytes (Quarkus {@code ArcTestContainer.index(beanClasses)}
mport com.github.dropguard.summer.core.Internal;
     * shape), and the production slice is empty — discovery only ever sees the listed graph, never
mport com.github.dropguard.summer.core.Internal;
     * any {@code jandex.idx} and never a negative fixture outside the seed closure.
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public static BeanDeployment forNarrow(IndexView narrowIndex) {
mport com.github.dropguard.summer.core.Internal;
        Map<String, String> classToArchive = new java.util.HashMap<>();
mport com.github.dropguard.summer.core.Internal;
        Map<String, IndexView> archiveIndexes = new java.util.HashMap<>();
mport com.github.dropguard.summer.core.Internal;
        for (String typeName :
mport com.github.dropguard.summer.core.Internal;
                narrowIndex.getKnownClasses().stream().map(Object::toString).toList()) {
mport com.github.dropguard.summer.core.Internal;
            classToArchive.put(typeName, "narrow");
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        archiveIndexes.put("narrow", narrowIndex);
mport com.github.dropguard.summer.core.Internal;
        return new BeanDeployment(classToArchive, archiveIndexes);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Builds a production deployment: the application archives only, no test slice. Used by runtime
mport com.github.dropguard.summer.core.Internal;
     * startup and AOT code generation — a test class can never enter this deployment.
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public static BeanDeployment forProduction(
mport com.github.dropguard.summer.core.Internal;
            IndexView productionIndex,
mport com.github.dropguard.summer.core.Internal;
            Map<String, String> classToArchive,
mport com.github.dropguard.summer.core.Internal;
            Map<String, IndexView> archiveIndexes) {
mport com.github.dropguard.summer.core.Internal;
        return new BeanDeployment(productionIndex, classToArchive, archiveIndexes);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Builds a production deployment from a single merged index, treating it as one archive named
mport com.github.dropguard.summer.core.Internal;
     * {@code "production"}. Convenience overload mirroring {@link #forNarrow(IndexView)} so callers
mport com.github.dropguard.summer.core.Internal;
     * don't have to build the archive maps by hand — the whole index is the universe, as production
mport com.github.dropguard.summer.core.Internal;
     * expects.
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public static BeanDeployment forProduction(IndexView productionIndex) {
mport com.github.dropguard.summer.core.Internal;
        Map<String, String> classToArchive = new java.util.HashMap<>();
mport com.github.dropguard.summer.core.Internal;
        Map<String, IndexView> archiveIndexes = new java.util.HashMap<>();
mport com.github.dropguard.summer.core.Internal;
        for (String typeName :
mport com.github.dropguard.summer.core.Internal;
                productionIndex.getKnownClasses().stream().map(Object::toString).toList()) {
mport com.github.dropguard.summer.core.Internal;
            classToArchive.put(typeName, "production");
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        archiveIndexes.put("production", productionIndex);
mport com.github.dropguard.summer.core.Internal;
        return new BeanDeployment(productionIndex, classToArchive, archiveIndexes);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /** Production archives only ({@code jandex.idx}). Never test classes. */
mport com.github.dropguard.summer.core.Internal;
    public IndexView productionIndex() {
mport com.github.dropguard.summer.core.Internal;
        return productionIndex;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * The merged discovery view: every raw per-archive index (production and test) composed into
mport com.github.dropguard.summer.core.Internal;
     * one composite. This is the single point where the two slices combine, and it happens at
mport com.github.dropguard.summer.core.Internal;
     * deployment-assembly time, never by collapsing archives at load time. The merge is flat (every
mport com.github.dropguard.summer.core.Internal;
     * archive as one element) so {@code getClassByName} / {@code getKnownClasses} keep the
mport com.github.dropguard.summer.core.Internal;
     * semantics discovery and bean-enrichment rely on.
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public IndexView discoveryIndex() {
mport com.github.dropguard.summer.core.Internal;
        List<IndexView> all = new ArrayList<>(archiveIndexes.values());
mport com.github.dropguard.summer.core.Internal;
        if (all.isEmpty()) {
mport com.github.dropguard.summer.core.Internal;
            return CompositeIndex.create(List.of());
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return CompositeIndex.create(all);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /** All archive names. */
mport com.github.dropguard.summer.core.Internal;
    public Set<String> archives() {
mport com.github.dropguard.summer.core.Internal;
        return archiveIndexes.keySet();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /** All indexed type names, cached from the class-to-archive map. */
mport com.github.dropguard.summer.core.Internal;
    public Set<String> allTypeNames() {
mport com.github.dropguard.summer.core.Internal;
        return allTypeNames;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Returns the archive name a class belongs to, or {@code null} if the class is not indexed.
mport com.github.dropguard.summer.core.Internal;
     * This is the boundary key for {@code @ConditionalOnBean} visibility: a condition is satisfied
mport com.github.dropguard.summer.core.Internal;
     * only by beans in the same archive.
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public String archiveOf(String className) {
mport com.github.dropguard.summer.core.Internal;
        return classToArchive.get(className);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /** The raw {@link IndexView} for a single archive. */
mport com.github.dropguard.summer.core.Internal;
    public IndexView archiveIndex(String archiveName) {
mport com.github.dropguard.summer.core.Internal;
        return archiveIndexes.get(archiveName);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Registers an engine-provided (synthetic) bean to be merged into the candidate set by {@code
mport com.github.dropguard.summer.core.Internal;
     * Discovery}. The instance is stored directly on the resulting {@link BeanDefinition} ({@code
mport com.github.dropguard.summer.core.Internal;
     * syntheticInstance}) so the runtime engine registers it without re-instantiating. {@code
mport com.github.dropguard.summer.core.Internal;
     * aotExpression} is the Java source the AOT engine emits to obtain the same instance at build
mport com.github.dropguard.summer.core.Internal;
     * time — supplied here at the definition site so the code generator never needs to know how
mport com.github.dropguard.summer.core.Internal;
     * each synthetic type is constructed.
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public void addSyntheticBean(Class<?> type, Object instance, String aotExpression) {
mport com.github.dropguard.summer.core.Internal;
        BeanDefinition bd = new BeanDefinition(type.getName(), type.getSimpleName());
mport com.github.dropguard.summer.core.Internal;
        bd.syntheticInstance = instance;
mport com.github.dropguard.summer.core.Internal;
        bd.aotInstanceExpression = aotExpression;
mport com.github.dropguard.summer.core.Internal;
        syntheticBeans.add(bd);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /** Engine-provided (synthetic) beans declared for this deployment. */
mport com.github.dropguard.summer.core.Internal;
    public List<BeanDefinition> syntheticBeans() {
mport com.github.dropguard.summer.core.Internal;
        return syntheticBeans;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
