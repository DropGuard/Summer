package summer.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads and merges Jandex indexes from the classpath.
 *
 * <p>
 * This is the infrastructure layer of Phase 1 (Discovery). It handles:
 * </p>
 * <ul>
 * <li>Runtime indexing of user packages (file: and jar: protocols)</li>
 * <li>Loading pre-built {@code META-INF/jandex.idx} from framework JARs</li>
 * <li>Merging all indexes into a single {@link CompositeIndex}</li>
 * </ul>
 *
 * <p>
 * This class does NOT resolve annotations or discover beans — that is the
 * responsibility of {@link ComponentScanner}.
 * </p>
 */
final class JandexIndexLoader {

	private static final Logger log = LoggerFactory.getLogger(JandexIndexLoader.class);

	private JandexIndexLoader() {
	}

	/**
	 * Builds a merged Jandex index from user packages and framework JARs.
	 *
	 * @param packageNames
	 *            user packages to index at runtime
	 * @return the merged index
	 */
	static IndexView buildIndex(String... packageNames) {
		Indexer indexer = new Indexer();

		// 1. Index user packages
		for (String packageName : packageNames) {
			indexUserPackage(indexer, packageName);
		}

		Index userIndex = indexer.complete();

		// 2. Load pre-built framework indexes
		List<Index> frameworkIndexes = loadFrameworkIndexes();

		if (frameworkIndexes.isEmpty()) {
			return userIndex;
		}

		// 3. Merge: user + framework
		List<IndexView> allIndexes = new ArrayList<>();
		allIndexes.add(userIndex);
		allIndexes.addAll(frameworkIndexes);
		return CompositeIndex.create(allIndexes);
	}

	/**
	 * Indexes user packages at runtime. Handles both file: and jar: classpath
	 * entries.
	 */
	private static void indexUserPackage(Indexer indexer, String packageName) {
		String packagePath = packageName.replace('.', '/');
		try {
			Enumeration<URL> resources = JandexIndexLoader.class.getClassLoader().getResources(packagePath);
			while (resources.hasMoreElements()) {
				URL resource = resources.nextElement();
				if ("file".equals(resource.getProtocol())) {
					indexFileResource(indexer, resource);
				} else if ("jar".equals(resource.getProtocol())) {
					tryIndexJar(indexer, resource);
				}
			}
		} catch (IOException e) {
			log.warn("[Summer] Failed to index package {}: {}", packageName, e.getMessage());
		}
	}

	private static void indexFileResource(Indexer indexer, URL resource) {
		try {
			Path dir = Paths.get(resource.toURI());
			if (Files.isDirectory(dir)) {
				indexDirectory(indexer, dir);
			}
		} catch (URISyntaxException | IOException e) {
			log.debug("[Summer] Could not index resource: {}", resource);
		}
	}

	private static void indexDirectory(Indexer indexer, Path dir) throws IOException {
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.class")) {
			for (Path classFile : stream) {
				try (InputStream is = Files.newInputStream(classFile)) {
					indexer.index(is);
				}
			}
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
			for (Path entry : stream) {
				if (Files.isDirectory(entry)) {
					indexDirectory(indexer, entry);
				}
			}
		}
	}

	private static void tryIndexJar(Indexer indexer, URL url) {
		String urlStr = url.toString();
		if (!urlStr.startsWith("jar:file:"))
			return;
		String jarPath = urlStr.substring("jar:file:".length());
		int bangIndex = jarPath.indexOf('!');
		if (bangIndex > 0)
			jarPath = jarPath.substring(0, bangIndex);

		try (JarFile jar = new JarFile(jarPath)) {
			String prefix = urlStr.substring(urlStr.indexOf('!') + 2);
			Enumeration<? extends ZipEntry> entries = jar.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (entry.getName().startsWith(prefix) && entry.getName().endsWith(".class") && !entry.isDirectory()) {
					try (InputStream is = jar.getInputStream(entry)) {
						indexer.index(is);
					}
				}
			}
		} catch (IOException e) {
			log.debug("[Summer] Could not index JAR {}: {}", jarPath, e.getMessage());
		}
	}

	/**
	 * Loads pre-built {@code META-INF/jandex.idx} files from all framework JARs on
	 * the classpath.
	 */
	private static List<Index> loadFrameworkIndexes() {
		List<Index> indexes = new ArrayList<>();
		try {
			Enumeration<URL> urls = JandexIndexLoader.class.getClassLoader().getResources("META-INF/jandex.idx");
			while (urls.hasMoreElements()) {
				URL url = urls.nextElement();
				loadIndexFromUrl(url, indexes);
			}
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
