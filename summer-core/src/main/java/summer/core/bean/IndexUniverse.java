package summer.core.bean;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.ErrorCode;
import summer.core.exception.ConfigurationException;

/**
 * Single source of truth for loading and merging the framework's pre-built
 * Jandex indexes.
 *
 * <p>
 * The build produces two classpath slices per module:
 * <ul>
 * <li>{@code META-INF/jandex.idx} — production classes (compile phase)</li>
 * <li>{@code META-INF/jandex-test.idx} — test classes (test-compile phase)</li>
 * </ul>
 * This split is a <b>storage</b> concern driven by the build tool: the
 * production / AOT-generation path must never see test classes, so the two
 * slices are kept apart on disk. <b>Merging</b> happens in memory, here, and
 * the merge rule defines the <em>universe</em> a consumer operates in.
 *
 * <p>
 * Every module that needs to discover beans — the DI engines, the data module's
 * {@code RowMapperRegistrar}, future redis/grpc discovery — MUST obtain its
 * index through this class, never by calling
 * {@code ClassLoader.getResources("META-INF/jandex.idx")} directly. That is the
 * contract that keeps test-tree beans (e.g. a {@code @RowModel} fixture)
 * visible to every consumer exactly the way the DI engines see them.
 * </p>
 */
public final class IndexUniverse {

	private static final Logger log = LoggerFactory.getLogger(IndexUniverse.class);

	/** Production index resource (no test classes). */
	public static final String PRODUCTION_INDEX = "META-INF/jandex.idx";
	/** Test index resource (test fixtures, test beans). */
	public static final String TEST_INDEX = "META-INF/jandex-test.idx";

	private IndexUniverse() {
	}

	/**
	 * The <b>application</b> universe: production classes only. Merges every
	 * {@code jandex.idx} and excludes {@code jandex-test.idx}, so an AOT-generated
	 * container can never instantiate a test class. Used by production startup and
	 * AOT code generation.
	 */
	public static IndexView applicationIndexView() {
		List<IndexView> indexes = loadResource(PRODUCTION_INDEX);
		if (indexes.isEmpty()) {
			throw new ConfigurationException(ErrorCode.CONFIG_MISSING_INDEX,
					"No " + PRODUCTION_INDEX + " found on classpath. "
							+ "Ensure jandex-maven-plugin is configured for modules that ship beans.");
		}
		return CompositeIndex.create(indexes);
	}

	/**
	 * The <b>test</b> universe: production classes plus every test-class bean on
	 * the test classpath. Merges {@code jandex.idx} AND {@code jandex-test.idx} per
	 * module, so a {@code @SummerTest} container — and any module discovery running
	 * inside it — sees the same beans as Quarkus' {@code @QuarkusTest}: the whole
	 * application plus whatever test beans are on the classpath.
	 *
	 * <p>
	 * This is the only universe test-scoped discovery should use. The split between
	 * the two resources exists purely so the <em>production</em> path never sees
	 * test classes; it is not a mechanism for excluding test beans from a test
	 * container.
	 * </p>
	 */
	public static IndexView testIndexView() {
		List<IndexView> indexes = new ArrayList<>();
		indexes.addAll(loadResource(PRODUCTION_INDEX));
		indexes.addAll(loadResource(TEST_INDEX));
		if (indexes.isEmpty()) {
			throw new ConfigurationException(ErrorCode.CONFIG_MISSING_INDEX,
					"No " + PRODUCTION_INDEX + " found on classpath.");
		}
		return CompositeIndex.create(indexes);
	}

	/**
	 * Loads every classpath resource with the given name into a list of
	 * {@link IndexView}s. Failures to read an individual resource are logged and
	 * skipped, never fatal — a missing or malformed index in one module must not
	 * abort discovery for the rest.
	 */
	private static List<IndexView> loadResource(String resourceName) {
		List<IndexView> indexes = new ArrayList<>();
		try {
			Enumeration<URL> urls = IndexUniverse.class.getClassLoader().getResources(resourceName);
			while (urls.hasMoreElements()) {
				URL url = urls.nextElement();
				try (InputStream is = url.openStream()) {
					indexes.add(new IndexReader(is).read());
				} catch (IOException e) {
					log.debug("[Summer] Failed to read {} from {}: {}", resourceName, url, e.getMessage());
				}
			}
		} catch (IOException e) {
			log.warn("[Summer] Failed to enumerate {} resources: {}", resourceName, e.getMessage());
		}
		return indexes;
	}
}
