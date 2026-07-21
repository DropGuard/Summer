package summer.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.ErrorCode;
import summer.core.exception.ConfigurationException;

/**
 * Loads pre-built Jandex indexes from the classpath.
 *
 * <p>
 * This is the single source of truth for reading the <b>production</b> index.
 * It enforces the Quarkus-style <b>disjoint-pipeline</b> model: the production
 * pipeline and the test pipeline are entirely separate concerns.
 * </p>
 *
 * <ul>
 * <li><b>Production / AOT pipeline</b> — every {@code META-INF/jandex.idx} on
 * the classpath (one per module, written at compile time). Read <em>only</em>
 * by this loader's {@link #productionIndex()}. A test class is never part of
 * this result, so a production container (runtime startup or AOT code
 * generation) can never instantiate a test bean. This is the boundary that
 * keeps {@code SummerMojo}-generated AOT containers free of test classes.</li>
 * <li><b>Test pipeline</b> — has <em>no</em> pre-baked index file. A
 * whole-universe {@code @SummerTest} container indexes the current test class's
 * {@code test-classes} directory on demand (see {@code TestClassIndexer}),
 * exactly like Quarkus' {@code @QuarkusTest} re-indexing its own test-classes
 * as an additional archive. A narrow {@code @SummerTest(classes=...)} container
 * reads the named {@code .class} bytes directly ({@code NarrowIndexBuilder}).
 * Because the test pipeline is built from {@code .class} bytes — never from a
 * merged {@code jandex-test.idx} — there is no full classpath sweep and no
 * exclude list; a negative (sad-path) fixture, which lives in its own module,
 * is simply not on the path of any application's {@code test-classes}
 * directory.</li>
 * </ul>
 */
public final class JandexIndexLoader {

	private static final Logger log = LoggerFactory.getLogger(JandexIndexLoader.class);

	/** Production index resource (no test classes). */
	public static final String PRODUCTION_INDEX = "META-INF/jandex.idx";

	private JandexIndexLoader() {
	}

	/**
	 * The production pipeline: every {@code META-INF/jandex.idx} on the classpath.
	 * Test-class indexes are <b>never</b> part of this result — so a production
	 * container (runtime startup or AOT generation) can never instantiate a test
	 * bean. This is the boundary that keeps {@code SummerMojo}-generated AOT
	 * containers free of test classes.
	 *
	 * @return the production view and its per-archive maps
	 */
	public static LoadedIndex productionIndex() {
		Map<String, String> classToArchive = new HashMap<>();
		Map<String, IndexView> archiveIndexes = new HashMap<>();
		loadIndexResource(PRODUCTION_INDEX, classToArchive, archiveIndexes);
		if (archiveIndexes.isEmpty()) {
			throw new ConfigurationException(ErrorCode.CONFIG_MISSING_INDEX, "No " + PRODUCTION_INDEX
					+ " found on classpath. Ensure jandex-maven-plugin is configured for modules that ship beans.");
		}
		List<IndexView> indexViews = new ArrayList<>(archiveIndexes.values());
		return new LoadedIndex(CompositeIndex.create(indexViews), classToArchive, archiveIndexes);
	}

	/**
	 * Convenience for debugging: the production index only.
	 *
	 * @see #productionIndex()
	 */
	public static IndexView buildIndex() {
		return productionIndex().index();
	}

	/**
	 * Holds a loaded index together with the per-archive attribution maps, so the
	 * caller ({@code BeanDeployment}) can build a production deployment without
	 * re-scanning the classpath.
	 */
	public static final class LoadedIndex {
		private final IndexView index;
		private final Map<String, String> classToArchive;
		private final Map<String, IndexView> archiveIndexes;

		LoadedIndex(IndexView index, Map<String, String> classToArchive, Map<String, IndexView> archiveIndexes) {
			this.index = index;
			this.classToArchive = classToArchive;
			this.archiveIndexes = archiveIndexes;
		}

		public IndexView index() {
			return index;
		}

		public Map<String, String> classToArchive() {
			return classToArchive;
		}

		public Map<String, IndexView> archiveIndexes() {
			return archiveIndexes;
		}
	}

	/**
	 * Loads every classpath resource with the given name, attributing each class to
	 * the module derived from its origin URL. Multiple copies of the same module on
	 * the classpath (e.g. {@code target/classes} and the installed jar) are merged
	 * into one archive index rather than last-wins, so a stale jar index cannot
	 * drop classes that the freshly compiled output still has.
	 */
	private static void loadIndexResource(String resourceName, Map<String, String> classToArchive,
			Map<String, IndexView> archiveIndexes) {
		Map<String, List<IndexView>> perModule = new HashMap<>();
		try {
			Enumeration<URL> urls = JandexIndexLoader.class.getClassLoader().getResources(resourceName);
			while (urls.hasMoreElements()) {
				URL url = urls.nextElement();
				String module = archiveFromUrl(url);
				log.debug("[Summer] Found {} at: {} (module={})", resourceName, url, module);

				Index index;
				try (InputStream is = url.openStream()) {
					index = new IndexReader(is).read();
				} catch (IOException e) {
					log.debug("[Summer] Failed to read {} from {}: {}", resourceName, url, e.getMessage());
					continue;
				}

				for (ClassInfo ci : index.getKnownClasses()) {
					classToArchive.put(ci.name().toString(), module);
				}
				perModule.computeIfAbsent(module, k -> new ArrayList<>()).add(index);
			}
			for (var entry : perModule.entrySet()) {
				List<IndexView> indexes = entry.getValue();
				archiveIndexes.put(entry.getKey(),
						indexes.size() == 1 ? indexes.get(0) : CompositeIndex.create(indexes));
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
	 * </p>
	 *
	 * <p>
	 * For directory-based indexes (local build output), uses the project directory
	 * name: {@code .../summer-core/target/classes/META-INF/jandex.idx} →
	 * {@code summer-core}.
	 * </p>
	 */
	static String archiveFromUrl(URL url) {
		String path = url.getPath();
		if (path.contains(".jar!")) {
			int start = path.lastIndexOf('/', path.lastIndexOf(".jar!")) + 1;
			String jarName = path.substring(start, path.indexOf(".jar!"));
			return jarName.replaceAll("-\\d+\\.\\d+.*", "");
		}
		String normalized = path.replace('\\', '/');
		int metaInfIdx = normalized.indexOf("/META-INF/");
		if (metaInfIdx > 0) {
			String beforeMetaInf = normalized.substring(0, metaInfIdx);
			if (beforeMetaInf.contains("/target/")) {
				int tgt = beforeMetaInf.lastIndexOf("/target/");
				int proj = beforeMetaInf.lastIndexOf('/', tgt - 1);
				return normalized.substring(proj + 1, tgt);
			}
			int classesIdx = beforeMetaInf.lastIndexOf("/classes");
			if (classesIdx > 0) {
				int projIdx = beforeMetaInf.lastIndexOf('/', classesIdx - 1);
				return normalized.substring(projIdx + 1, classesIdx);
			}
		}
		String[] parts = normalized.split("/");
		for (int i = 0; i < parts.length - 2; i++) {
			if ("META-INF".equals(parts[i + 1])) {
				return parts[i];
			}
		}
		return "unknown";
	}
}
