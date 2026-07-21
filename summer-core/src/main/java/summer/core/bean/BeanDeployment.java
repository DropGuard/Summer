package summer.core.bean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.IndexView;

/**
 * The unit of a single DI container build: the set of bean archives a container
 * is assembled from.
 *
 * <p>
 * This is the Quarkus {@code BeanDeployment} abstraction, copied faithfully. In
 * Quarkus the deployment holds two independently-sourced indexes:
 * {@code getBeanArchiveIndex()} (the application archives, built from
 * {@code META-INF/jandex.idx}) and {@code getApplicationIndex()} (extra types
 * needed only for type-safe resolution). The two are <b>never</b> collapsed
 * into one flat index at load time; discovery consults them as distinct slices.
 * </p>
 *
 * <p>
 * Summer mirrors that with two <b>disjoint pipelines</b>:
 * <ul>
 * <li><b>Production / AOT pipeline</b> — reads {@code jandex.idx} only
 * ({@link #productionIndex()}). It never touches a test class. This is the
 * boundary that keeps a generated AOT container free of test beans.</li>
 * <li><b>Test container pipeline</b> — the runtime reads {@code jandex.idx} for
 * the application archives, and builds the <em>test</em> slice on demand by
 * indexing exactly the running test class's {@code test-classes} directory via
 * {@code summer.runtime.TestClassIndexer} (Quarkus'
 * {@code TestClassIndexer.indexTestClasses} model). There is no pre-baked
 * {@code jandex-test.idx} and no bulk scan of every module's test classes. A
 * narrow {@code @SummerTest(classes=...)} container replaces the test slice
 * with a throwaway index built directly from the seed {@code .class} bytes
 * ({@link #forNarrow(IndexView)}), exactly like Quarkus'
 * {@code ArcTestContainer.index(beanClasses)}.</li>
 * </ul>
 * The two pipelines are disjoint by construction: the production loader and the
 * test loader come from different sources, and a negative (sad-path) fixture —
 * which lives in its own module — is simply not on the path of any
 * application's {@code test-classes} directory, so it cannot enter a
 * whole-universe container. No path glob, no allow-list, no post-hoc filtering
 * of a merged view — the isolation lives in which archives each pipeline
 * collects.
 * </p>
 *
 * <p>
 * An <b>archive</b> is the immutable unit of discovery: one jar (or one build
 * output directory) is one archive, named after the artifact it ships in.
 * Archives are isolated for {@code @ConditionalOnBean} visibility — a bean's
 * condition is satisfied only by another bean in the <em>same</em> archive (see
 * {@link #archiveOf(String)}). Bean <em>injection</em> remains global; only the
 * condition-evaluation boundary is archive-scoped.
 * </p>
 */
public final class BeanDeployment {

	private final IndexView productionIndex;
	private final Map<String, String> classToArchive;
	private final Set<String> allTypeNames;
	private final Map<String, IndexView> archiveIndexes;

	private BeanDeployment(IndexView productionIndex, Map<String, String> classToArchive,
			Map<String, IndexView> archiveIndexes) {
		this.productionIndex = productionIndex;
		this.classToArchive = Collections.unmodifiableMap(classToArchive);
		this.allTypeNames = Collections.unmodifiableSet(classToArchive.keySet());
		this.archiveIndexes = Collections.unmodifiableMap(archiveIndexes);
	}

	/**
	 * Narrow deployment: no production slice, discovery sees only the seed closure.
	 */
	private BeanDeployment(Map<String, String> classToArchive, Map<String, IndexView> archiveIndexes) {
		this.productionIndex = null;
		this.classToArchive = Collections.unmodifiableMap(classToArchive);
		this.allTypeNames = Collections.unmodifiableSet(classToArchive.keySet());
		this.archiveIndexes = Collections.unmodifiableMap(archiveIndexes);
	}

	/**
	 * Builds a whole-universe test deployment: the application archives plus the
	 * current test class's {@code test-classes} archive (indexed on demand by
	 * {@code summer.runtime.TestClassIndexer}, supplied inside
	 * {@code archiveIndexes} under the {@code "test"} key). The two slices stay
	 * separate as distinct archives; {@link #discoveryIndex()} merges them on
	 * demand for discovery.
	 */
	public static BeanDeployment forTestUniverse(IndexView productionIndex, Map<String, String> classToArchive,
			Map<String, IndexView> archiveIndexes) {
		return new BeanDeployment(productionIndex, classToArchive, archiveIndexes);
	}

	/**
	 * Builds a narrow (scoped) test deployment: the test slice is a throwaway index
	 * built directly from the seed {@code .class} bytes (Quarkus
	 * {@code ArcTestContainer.index(beanClasses)} shape), and the production slice
	 * is empty — discovery only ever sees the listed graph, never any
	 * {@code jandex.idx} and never a negative fixture outside the seed closure.
	 */
	public static BeanDeployment forNarrow(IndexView narrowIndex) {
		Map<String, String> classToArchive = new java.util.HashMap<>();
		Map<String, IndexView> archiveIndexes = new java.util.HashMap<>();
		for (String typeName : narrowIndex.getKnownClasses().stream().map(Object::toString).toList()) {
			classToArchive.put(typeName, "narrow");
		}
		archiveIndexes.put("narrow", narrowIndex);
		return new BeanDeployment(classToArchive, archiveIndexes);
	}

	/**
	 * Builds a production deployment: the application archives only, no test slice.
	 * Used by runtime startup and AOT code generation — a test class can never
	 * enter this deployment.
	 */
	public static BeanDeployment forProduction(IndexView productionIndex, Map<String, String> classToArchive,
			Map<String, IndexView> archiveIndexes) {
		return new BeanDeployment(productionIndex, classToArchive, archiveIndexes);
	}

	/** Production archives only ({@code jandex.idx}). Never test classes. */
	public IndexView productionIndex() {
		return productionIndex;
	}

	/**
	 * The merged discovery view: every raw per-archive index (production and test)
	 * composed into one composite. This is the single point where the two slices
	 * combine, and it happens at deployment-assembly time, never by collapsing
	 * archives at load time. The merge is flat (every archive as one element) so
	 * {@code getClassByName} / {@code getKnownClasses} keep the semantics discovery
	 * and bean-enrichment rely on.
	 */
	public IndexView discoveryIndex() {
		List<IndexView> all = new ArrayList<>(archiveIndexes.values());
		if (all.isEmpty()) {
			return CompositeIndex.create(List.of());
		}
		return CompositeIndex.create(all);
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
	 * Returns the archive name a class belongs to, or {@code null} if the class is
	 * not indexed. This is the boundary key for {@code @ConditionalOnBean}
	 * visibility: a condition is satisfied only by beans in the same archive.
	 */
	public String archiveOf(String className) {
		return classToArchive.get(className);
	}

	/** The raw {@link IndexView} for a single archive. */
	public IndexView archiveIndex(String archiveName) {
		return archiveIndexes.get(archiveName);
	}
}
