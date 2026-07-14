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
import summer.core.bean.ModuleIndex;
import summer.core.bean.Scope;
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
	 */
	public static ModuleIndex buildModuleIndex() {
		Map<String, String> classToModule = new HashMap<>();
		List<Index> indexes = new ArrayList<>();

		try {
			Enumeration<URL> urls = JandexIndexLoader.class.getClassLoader().getResources("META-INF/jandex.idx");
			while (urls.hasMoreElements()) {
				URL url = urls.nextElement();
				String module = moduleFromUrl(url);
				log.debug("[Summer] Found jandex.idx at: {} (module={})", url, module);

				Index index;
				try (InputStream is = url.openStream()) {
					index = new IndexReader(is).read();
				} catch (IOException e) {
					log.debug("[Summer] Failed to read jandex.idx from {}: {}", url, e.getMessage());
					continue;
				}

				// Tag each class with its module
				for (ClassInfo ci : index.getKnownClasses()) {
					classToModule.put(ci.name().toString(), module);
				}
				indexes.add(index);
			}
		} catch (IOException e) {
			log.warn("[Summer] Failed to enumerate jandex.idx resources: {}", e.getMessage());
		}

		if (indexes.isEmpty()) {
			throw new ConfigurationException(ErrorCode.CONFIG_MISSING_INDEX, "No jandex.idx found on classpath.");
		}

		List<IndexView> indexViews = new ArrayList<>(indexes);
		return new ModuleIndex(CompositeIndex.create(indexViews), classToModule);
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
