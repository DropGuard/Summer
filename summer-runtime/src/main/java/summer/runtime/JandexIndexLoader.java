package summer.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.ErrorCode;
import summer.core.bean.ModuleIndex;
import summer.core.exception.ConfigurationException;

/**
 * Loads and merges pre-built Jandex indexes ({@code META-INF/jandex.idx}) from
 * the classpath.
 *
 * <p>
 * Both the Runtime and AOT engines use the same discovery mechanism: pre-built
 * Jandex indexes. Modules that ship beans must have {@code jandex-maven-plugin}
 * configured to generate {@code META-INF/jandex.idx}.
 * </p>
 */
public final class JandexIndexLoader {

	private static final Logger log = LoggerFactory.getLogger(JandexIndexLoader.class);

	private JandexIndexLoader() {
	}

	/**
	 * Loads all {@code META-INF/jandex.idx} files from the classpath and merges
	 * them into a single index.
	 *
	 * @return the merged index
	 */
	public static IndexView buildIndex() {
		List<Index> indexes = loadIndexes();
		if (indexes.isEmpty()) {
			throw new ConfigurationException(ErrorCode.CONFIG_MISSING_INDEX, "No jandex.idx found on classpath. "
					+ "Ensure jandex-maven-plugin is configured for modules that ship beans.");
		}
		List<IndexView> indexViews = new ArrayList<>(indexes);
		return CompositeIndex.create(indexViews);
	}

	/**
	 * Loads Jandex indexes with module attribution.
	 *
	 * <p>
	 * Each indexed class is associated with the module it came from (derived from
	 * the jar or directory containing {@code META-INF/jandex.idx}). The returned
	 * {@link ModuleIndex} can be used to scope bean discovery to specific modules.
	 * </p>
	 *
	 * <p>
	 * In addition to production indexes ({@code jandex.idx}), test-class indexes
	 * ({@code jandex-test.idx}) are consulted purely for module attribution — so a
	 * {@code @SummerTest} class resolves to its own module even though test classes
	 * are not part of the bean-discovery universe. Test indexes are intentionally
	 * kept under a separate file name so the AOT generator (which scans
	 * {@code jandex.idx}) never treats test classes as beans.
	 * </p>
	 */
	public static ModuleIndex buildModuleIndex() {
		Map<String, String> classToModule = new HashMap<>();
		Map<String, IndexView> moduleIndexes = new HashMap<>();
		List<Index> indexes = new ArrayList<>();

		// Production/main indexes: attribution + bean-discovery universe.
		loadIndexResource("META-INF/jandex.idx", classToModule, moduleIndexes, indexes, true);
		// Test indexes: attribution only (not added to the bean universe).
		loadIndexResource("META-INF/jandex-test.idx", classToModule, moduleIndexes, indexes, false);

		if (indexes.isEmpty()) {
			throw new ConfigurationException(ErrorCode.CONFIG_MISSING_INDEX, "No jandex.idx found on classpath.");
		}

		List<IndexView> indexViews = new ArrayList<>(indexes);
		return new ModuleIndex(CompositeIndex.create(indexViews), classToModule, moduleIndexes);
	}

	/**
	 * Builds a {@link ModuleIndex} for the Runtime test container.
	 *
	 * <p>
	 * Like {@link #buildModuleIndex()}, but test-class indexes
	 * ({@code META-INF/jandex-test.idx}) are <b>also</b> added to the
	 * bean-discovery universe — not attribution-only. Test fixtures
	 * ({@code @Component} classes in {@code src/test}) must be instantiable by the
	 * container, yet they live in test-class indexes that the AOT generator never
	 * scans. Keeping them in the Runtime test universe is safe: the
	 * {@code ConflictConfig} ambiguity that motivated the index split is a
	 * <em>main-source</em> fixture (already in {@code jandex.idx}); it is
	 * unaffected because {@code SummerMojo} still reads only {@code jandex.idx}.
	 * </p>
	 *
	 * @return a module index whose bean universe includes test-class indexes
	 */
	public static ModuleIndex buildTestModuleIndex() {
		Map<String, String> classToModule = new HashMap<>();
		// Production per-module indexes (authoritative for the bean universe).
		Map<String, IndexView> prodIndexes = new HashMap<>();
		// Test per-module indexes (fixtures); merged onto prod when a module has both.
		Map<String, IndexView> testIndexes = new HashMap<>();
		List<Index> indexes = new ArrayList<>();

		// Production/main indexes: attribution + bean-discovery universe.
		loadIndexResource("META-INF/jandex.idx", classToModule, prodIndexes, indexes, true);
		// Test indexes: attribution + bean-discovery universe (test fixtures).
		loadIndexResource("META-INF/jandex-test.idx", classToModule, testIndexes, indexes, true);

		if (indexes.isEmpty()) {
			throw new ConfigurationException(ErrorCode.CONFIG_MISSING_INDEX, "No jandex.idx found on classpath.");
		}

		// Per-module index = production classes, merged with the module's test
		// classes when both exist. This lets discoverComponents iterate the full
		// class set for a module (production beans + its own test fixtures) instead
		// of the test index silently shadowing the production one.
		Map<String, IndexView> moduleIndexes = new HashMap<>(prodIndexes);
		for (var entry : testIndexes.entrySet()) {
			IndexView existing = moduleIndexes.get(entry.getKey());
			moduleIndexes.put(entry.getKey(),
					existing != null ? CompositeIndex.create(List.of(existing, entry.getValue())) : entry.getValue());
		}

		List<IndexView> indexViews = new ArrayList<>(indexes);
		return new ModuleIndex(CompositeIndex.create(indexViews), classToModule, moduleIndexes);
	}

	/**
	 * Builds a {@link ModuleIndex} from production indexes only
	 * ({@code jandex.idx}), excluding {@code jandex-test.idx}.
	 *
	 * <p>
	 * Used to decide whether a class is a production bean (and thus always in scope
	 * for an integration test) versus a test-only fixture that must be named
	 * explicitly. Keeping test indexes out means test fixtures are NOT treated as
	 * production beans.
	 * </p>
	 *
	 * @return a production-only module index
	 */
	public static ModuleIndex buildProductionModuleIndex() {
		Map<String, String> classToModule = new HashMap<>();
		Map<String, IndexView> moduleIndexes = new HashMap<>();
		List<Index> indexes = new ArrayList<>();
		loadIndexResource("META-INF/jandex.idx", classToModule, moduleIndexes, indexes, true);
		if (indexes.isEmpty()) {
			throw new ConfigurationException(ErrorCode.CONFIG_MISSING_INDEX, "No jandex.idx found on classpath.");
		}
		List<IndexView> indexViews = new ArrayList<>(indexes);
		return new ModuleIndex(CompositeIndex.create(indexViews), classToModule, moduleIndexes);
	}

	/** DotName constants for bean-type discovery. */
	private static final DotName COMPONENT_DN = DotName.createSimple("summer.core.Component");
	private static final DotName CONFIG_DN = DotName.createSimple("summer.core.annotation.Configuration");
	private static final DotName CONFIG_PROPS_DN = DotName.createSimple("summer.core.config.ConfigurationProperties");
	private static final DotName BEAN_DN = DotName.createSimple("summer.core.annotation.Bean");

	/**
	 * Computes the set of fully-qualified type names that are actual beans across
	 * the entire merged index. A type is a "bean type" when it is annotated with
	 * {@code @Component}, {@code @Configuration}, or
	 * {@code @ConfigurationProperties}, or when it is the return type of a
	 * {@code @Bean} factory method in any {@code @Configuration} class.
	 *
	 * <p>
	 * This set is used as {@code visibleTypes} in scoped discovery: it prevents
	 * {@code @ConditionalOnBean} from being satisfied by plain indexed types
	 * (unannotated POJOs or non-bean types) while still allowing cross-module
	 * visibility for real beans. Plain POJOs (e.g. {@code MissingComponent}) only
	 * satisfy the condition when they are actual beans in the container, not just
	 * because they appear in a Jandex index.
	 * </p>
	 *
	 * @param index
	 *            the merged Jandex index
	 * @return set of type names that are actual beans
	 */
	public static Set<String> computeBeanTypeNames(IndexView index) {
		Set<String> types = new HashSet<>();
		DotName componentDn = COMPONENT_DN;
		DotName configDn = CONFIG_DN;
		DotName configPropsDn = CONFIG_PROPS_DN;
		DotName beanDn = BEAN_DN;

		// Phase 1: scan all classes for @Component / @Configuration /
		// @ConfigurationProperties
		for (ClassInfo ci : index.getKnownClasses()) {
			if (ci.isAnnotation())
				continue;
			if (ci.hasAnnotation(componentDn) || ci.hasAnnotation(configDn) || ci.hasAnnotation(configPropsDn)) {
				types.add(ci.name().toString());
			}
		}

		// Phase 2: collect @Bean return types from @Configuration classes
		for (ClassInfo ci : index.getKnownClasses()) {
			if (!ci.hasAnnotation(configDn))
				continue;
			for (MethodInfo method : ci.methods()) {
				if (method.hasAnnotation(beanDn) && method.returnType() != null) {
					types.add(method.returnType().name().toString());
				}
			}
		}

		return types;
	}

	/**
	 * Loads every classpath resource with the given name, attributing each class to
	 * the module derived from its origin URL.
	 *
	 * @param resourceName
	 *            e.g. {@code META-INF/jandex.idx}
	 * @param classToModule
	 *            accumulator mapping class name → module
	 * @param moduleIndexes
	 *            accumulator mapping module name → its raw IndexView (only
	 *            populated when {@code includeInBeanUniverse} is true)
	 * @param indexes
	 *            accumulator of indexes to include in the bean-discovery universe
	 *            (skipped when {@code includeInBeanUniverse} is false)
	 */
	private static void loadIndexResource(String resourceName, Map<String, String> classToModule,
			Map<String, IndexView> moduleIndexes, List<Index> indexes, boolean includeInBeanUniverse) {
		try {
			Enumeration<URL> urls = JandexIndexLoader.class.getClassLoader().getResources(resourceName);
			while (urls.hasMoreElements()) {
				URL url = urls.nextElement();
				String module = moduleFromUrl(url);
				log.debug("[Summer] Found {} at: {} (module={})", resourceName, url, module);

				Index index;
				try (InputStream is = url.openStream()) {
					index = new IndexReader(is).read();
				} catch (IOException e) {
					log.debug("[Summer] Failed to read {} from {}: {}", resourceName, url, e.getMessage());
					continue;
				}

				for (ClassInfo ci : index.getKnownClasses()) {
					classToModule.put(ci.name().toString(), module);
				}
				if (includeInBeanUniverse) {
					moduleIndexes.put(module, index);
					indexes.add(index);
				}
			}
		} catch (IOException e) {
			log.warn("[Summer] Failed to enumerate {} resources: {}", resourceName, e.getMessage());
		}
	}

	/**
	 * Derives the module name from a {@code META-INF/jandex.idx} URL.
	 *
	 * <p>
	 * For jar files, uses the artifact name (stripped of version):
	 * {@code summer-data-jdbc-0.1.0.jar} → {@code summer-data-jdbc}.
	 *
	 * <p>
	 * For directory-based indexes (local build output), uses the project directory
	 * name: {@code .../summer-core/target/classes/META-INF/jandex.idx} →
	 * {@code summer-core}.
	 */
	static String moduleFromUrl(URL url) {
		String path = url.getPath();
		// Jar URL: "file:.../summer-twitter-0.1.0.jar!/META-INF/jandex.idx"
		// Extract jar name, strip "-<version>"
		if (path.contains(".jar!")) {
			int start = path.lastIndexOf('/', path.lastIndexOf(".jar!")) + 1;
			String jarName = path.substring(start, path.indexOf(".jar!"));
			// Strip trailing "-<version>" (e.g. summer-twitter-0.1.0 → summer-twitter)
			return jarName.replaceAll("-\\d+\\.\\d+.*", "");
		}
		// Directory URL: ".../summer-core/target/classes/META-INF/jandex.idx"
		// Walk up from "META-INF" to find the project directory
		String normalized = path.replace('\\', '/');
		int metaInfIdx = normalized.indexOf("/META-INF/");
		if (metaInfIdx > 0) {
			String beforeMetaInf = normalized.substring(0, metaInfIdx);
			// .../summer-core/target/classes → find first segment that has no "target/"
			// Actually, just take the last segment before target/ or classes/
			if (beforeMetaInf.contains("/target/")) {
				int tgt = beforeMetaInf.lastIndexOf("/target/");
				int proj = beforeMetaInf.lastIndexOf('/', tgt - 1);
				return normalized.substring(proj + 1, tgt);
			}
			// Fallback: segment right before "classes/"
			int classesIdx = beforeMetaInf.lastIndexOf("/classes");
			if (classesIdx > 0) {
				int projIdx = beforeMetaInf.lastIndexOf('/', classesIdx - 1);
				return normalized.substring(projIdx + 1, classesIdx);
			}
		}
		// Last resort: use the dir that contains META-INF
		String[] parts = normalized.split("/");
		for (int i = 0; i < parts.length - 2; i++) {
			if ("META-INF".equals(parts[i + 1])) {
				return parts[i];
			}
		}
		return "unknown";
	}

	private static List<Index> loadIndexes() {
		List<Index> indexes = new ArrayList<>();
		try {
			Enumeration<URL> urls = JandexIndexLoader.class.getClassLoader().getResources("META-INF/jandex.idx");
			while (urls.hasMoreElements()) {
				URL url = urls.nextElement();
				log.debug("[Summer] Found jandex.idx at: {}", url);
				loadIndexFromUrl(url, indexes);
			}
			log.debug("[Summer] Loaded {} index(es)", indexes.size());
		} catch (IOException e) {
			log.warn("[Summer] Failed to enumerate jandex.idx resources: {}", e.getMessage());
		}
		return indexes;
	}

	private static void loadIndexFromUrl(URL url, List<Index> indexes) {
		try (InputStream is = url.openStream()) {
			indexes.add(new IndexReader(is).read());
		} catch (IOException e) {
			log.debug("[Summer] Failed to read jandex.idx from {}: {}", url, e.getMessage());
		}
	}
}
