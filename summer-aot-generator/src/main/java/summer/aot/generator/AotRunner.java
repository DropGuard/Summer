package summer.aot.generator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standalone AOT code generator for Summer framework. Reads Jandex indexes from
	 * classpath entries and generates per-module initializers + AOT entry point.
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

		// 1. Load all Jandex indexes
		CompositeIndex index = loadIndexes(args);
		log.info("[Summer] Loaded Jandex index with {} classes", index.getKnownClasses().size());

		// 2. Discover beans (full pipeline: discovery + enrichment + conditions)
		List<BeanDefinition> beans = new BeanDiscovery(index).discover(null);
		log.info("[Summer] Discovered {} beans", beans.size());

		// 3. All beans go into a single "app" module (no artifact info available)
		String moduleId = "app";
		for (BeanDefinition bean : beans) {
			bean.moduleId = moduleId;
		}

		// 4. Collect all bean qualified names
		Set<String> allBeanNames = new HashSet<>();
		for (ClassInfo ci : index.getKnownClasses()) {
			allBeanNames.add(ci.name().toString());
		}

		// 5. Resolve dependencies
		DependencyResolver resolver = new DependencyResolver();
		List<BeanDefinition> sorted = resolver.resolve(beans);
		log.info("[Summer] Resolved dependencies for {} beans", sorted.size());

		// 6. Generate DefaultValueResolver for @ConfigurationProperties
		List<ConfigPropertiesBean> configBeans = sorted.stream()
				.filter(ConfigPropertiesBean.class::isInstance)
				.map(ConfigPropertiesBean.class::cast)
				.toList();
		new DefaultValueResolverGenerator().generate(configBeans, outputDir);

		// 7. Generate AOT entry point
		new AppBootstrapGenerator().generate(sorted, index, outputDir);

		// 9. Generate proxies and routes
		new AotProxyGenerator().generate(sorted, outputDir);
		new RouteAdapterGenerator().generate(sorted, outputDir);

		log.info("[Summer] AOT code generation complete");
	}

	private static CompositeIndex loadIndexes(String[] args) throws IOException {
		List<IndexView> indexes = new ArrayList<>();
		Set<String> seen = new HashSet<>();

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
