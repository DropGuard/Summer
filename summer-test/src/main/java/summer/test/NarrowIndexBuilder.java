package summer.test;

import java.io.InputStream;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;

/**
 * Builds a Jandex {@link IndexView} for a narrow bean universe, used by
 * {@code @SummerTest(classes=...)} scoped tests.
 *
 * <p>
 * This is a faithful port of Quarkus'
 * {@code ArcTestContainer.index(Class<?>...)}
 * (independent-projects/arc/tests/src/test/java/io/quarkus/arc/test/ArcTestContainer.java:642):
 * it indexes <em>exactly</em> the given seed classes plus each seed's
 * {@code package-info}, and nothing else. There is deliberately <b>no</b>
 * transitive-closure (BFS) expansion — Quarkus does not widen the seed set; the
 * caller is responsible for listing every bean the test needs (including
 * dependencies). Mirroring that contract keeps Summer's narrow path behaviour
 * identical to Quarkus' {@code beanClasses(...)} path.
 * </p>
 *
 * <p>
 * The result is a self-contained index that the DI engines discover and wire as
 * the whole universe for that test — so a test listing
 * {@code CycleNodeA, CycleNodeB} sees exactly those classes and nothing else
 * (which is what makes a broken graph fail assembly as the test promises).
 * </p>
 */
final class NarrowIndexBuilder {

	private NarrowIndexBuilder() {
	}

	static IndexView build(Class<?>... seeds) {
		Indexer indexer = new Indexer();
		for (Class<?> seed : seeds) {
			indexClass(seed, indexer);
			indexPackageInfo(seed, indexer);
		}
		return indexer.complete();
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
