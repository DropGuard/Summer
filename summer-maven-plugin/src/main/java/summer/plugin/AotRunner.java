package summer.plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AOT code generator for Summer framework.
 * 
 * <p>
 * This class reads Jandex indexes from all dependencies and generates AOT
 * context classes at compile time. It has full classpath access, solving the
 * annotation processor isolation problem.
 * </p>
 * 
 * <p>
 * Usage: java summer.plugin.AotRunner [outputDir] [classpathEntries...]
 * </p>
 */
public class AotRunner {

	private static final Logger log = LoggerFactory.getLogger(AotRunner.class);

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			log.error("Usage: AotRunner <outputDir> [classpathEntries...]");
			System.exit(1);
		}

		File outputDir = new File(args[0]);
		outputDir.mkdirs();

		log.info("[Summer] Starting AOT code generation...");
		log.info("[Summer] Output directory: {}", outputDir.getAbsolutePath());

		// 1. Load all Jandex indexes
		CompositeIndex index = loadIndexes(args);
		log.info("[Summer] Loaded Jandex index with {} classes", index.getKnownClasses().size());

		// 2. Discover beans
		List<BeanDefinition> beans = BeanDiscovery.discoverBeans(index, null);
		log.info("[Summer] Discovered {} beans", beans.size());

		// 3. Resolve dependencies
		DependencyResolver resolver = new DependencyResolver();
		List<BeanDefinition> sorted = resolver.resolve(beans);
		log.info("[Summer] Resolved dependencies for {} beans", sorted.size());

		// 4. Generate AOT code
		new AotContextGenerator().generate(sorted, outputDir);
		new AotProxyGenerator().generate(sorted, index, outputDir);
		new RouteAdapterGenerator().generate(sorted, outputDir);

		log.info("[Summer] AOT code generation complete");
	}

	private static CompositeIndex loadIndexes(String[] args) throws IOException {
		List<IndexView> indexes = new ArrayList<>();
		Set<String> seen = new HashSet<>();

		// Args[1..] are classpath entries to scan for Jandex indexes
		for (int i = 1; i < args.length; i++) {
			String entry = args[i];
			File file = new File(entry);
			if (!file.exists()) {
				continue;
			}

			if (file.isDirectory()) {
				Path indexPath = file.toPath().resolve("META-INF").resolve("jandex.idx");
				if (Files.exists(indexPath) && seen.add(indexPath.toString())) {
					try (InputStream is = Files.newInputStream(indexPath)) {
						indexes.add(new IndexReader(is).read());
						log.info("[Summer] Loaded index from: {}", file.getName());
					}
				}
			} else if (file.getName().endsWith(".jar")) {
				try (java.util.jar.JarFile jar = new java.util.jar.JarFile(file)) {
					java.util.jar.JarEntry entry2 = jar.getJarEntry("META-INF/jandex.idx");
					if (entry2 != null && seen.add(file.getAbsolutePath())) {
						try (InputStream is = jar.getInputStream(entry2)) {
							indexes.add(new IndexReader(is).read());
							log.info("[Summer] Loaded index from: {}", file.getName());
						}
					}
				} catch (Exception e) {
					// Skip invalid JARs
				}
			}
		}

		if (indexes.isEmpty()) {
			log.warn("[Summer] No Jandex indexes found!");
			return CompositeIndex.create(new ArrayList<>());
		}

		return CompositeIndex.create(indexes);
	}
}
