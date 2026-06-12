package summer.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
final class JandexIndexLoader {

	private static final Logger log = LoggerFactory.getLogger(JandexIndexLoader.class);

	private JandexIndexLoader() {
	}

	/**
	 * Loads all {@code META-INF/jandex.idx} files from the classpath and merges
	 * them into a single index.
	 *
	 * @return the merged index
	 */
	static IndexView buildIndex() {
		List<Index> indexes = loadIndexes();
		if (indexes.isEmpty()) {
			log.warn("[Summer] No jandex.idx found on classpath. "
					+ "Ensure jandex-maven-plugin is configured for modules that ship beans.");
			return CompositeIndex.create(new ArrayList<>());
		}
		List<IndexView> indexViews = new ArrayList<>(indexes);
		return CompositeIndex.create(indexViews);
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
