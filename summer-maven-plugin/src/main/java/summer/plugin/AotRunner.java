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
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;

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

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.err.println("Usage: AotRunner <outputDir> [classpathEntries...]");
			System.exit(1);
		}

		File outputDir = new File(args[0]);
		outputDir.mkdirs();

		System.out.println("[Summer] Starting AOT code generation...");
		System.out.println("[Summer] Output directory: " + outputDir.getAbsolutePath());

		// 1. Load all Jandex indexes
		CompositeIndex index = loadIndexes(args);
		System.out.println("[Summer] Loaded Jandex index with " + index.getKnownClasses().size() + " classes");

		// 2. Discover beans
		List<BeanDefinition> beans = discoverBeans(index);
		System.out.println("[Summer] Discovered " + beans.size() + " beans");

		// 3. Resolve dependencies
		DependencyResolver resolver = new DependencyResolver();
		List<BeanDefinition> sorted = resolver.resolve(beans);
		System.out.println("[Summer] Resolved dependencies for " + sorted.size() + " beans");

		// 4. Generate AOT code
		new AotContextGenerator().generate(sorted, outputDir);
		new AotProxyGenerator().generate(sorted, outputDir);
		new RouteAdapterGenerator().generate(sorted, outputDir);

		System.out.println("[Summer] AOT code generation complete");
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
						System.out.println("[Summer] Loaded index from: " + file.getName());
					}
				}
			} else if (file.getName().endsWith(".jar")) {
				try (java.util.jar.JarFile jar = new java.util.jar.JarFile(file)) {
					java.util.jar.JarEntry entry2 = jar.getJarEntry("META-INF/jandex.idx");
					if (entry2 != null && seen.add(file.getAbsolutePath())) {
						try (InputStream is = jar.getInputStream(entry2)) {
							indexes.add(new IndexReader(is).read());
							System.out.println("[Summer] Loaded index from: " + file.getName());
						}
					}
				} catch (Exception e) {
					// Skip invalid JARs
				}
			}
		}

		if (indexes.isEmpty()) {
			System.err.println("[Summer] WARNING: No Jandex indexes found!");
			return CompositeIndex.create(new ArrayList<>());
		}

		return CompositeIndex.create(indexes);
	}

	private static List<BeanDefinition> discoverBeans(CompositeIndex index) {
		List<BeanDefinition> beans = new ArrayList<>();
		Set<String> collected = new HashSet<>();

		DotName componentDot = DotName.createSimple("summer.core.Component");
		DotName configDot = DotName.createSimple("summer.core.annotation.Configuration");

		// Phase 1: Directly annotated beans
		for (ClassInfo ci : index.getKnownClasses()) {
			if (ci.isAnnotation()) {
				continue;
			}

			boolean isComponent = ci.hasAnnotation(componentDot);
			boolean isConfig = ci.hasAnnotation(configDot);

			if (isComponent || isConfig) {
				String qualifiedName = ci.name().toString();
				if (collected.add(qualifiedName)) {
					BeanDefinition bean = new BeanDefinition(
							isConfig ? BeanDefinition.Kind.CONFIGURATION : BeanDefinition.Kind.COMPONENT, qualifiedName,
							ci.simpleName());

					// Collect constructor parameters
					org.jboss.jandex.MethodInfo ctor = ci.firstMethod("<init>");
					if (ctor != null) {
						for (int i = 0; i < ctor.parametersCount(); i++) {
							bean.constructorParamTypes.add(ctor.parameterType(i).name().toString());
						}
					}

					// Collect interfaces
					for (org.jboss.jandex.Type iface : ci.interfaceTypes()) {
						bean.interfaceNames.add(iface.name().toString());
					}

					beans.add(bean);
				}
			}
		}

		// Phase 2: Meta-annotated components (e.g., @RestController)
		for (ClassInfo ci : index.getKnownClasses()) {
			if (!ci.isAnnotation()) {
				continue;
			}

			if (ci.hasAnnotation(componentDot)) {
				DotName metaAnnotationName = ci.name();
				for (ClassInfo usage : index.getKnownClasses()) {
					if (usage.isAnnotation() || usage.hasAnnotation(metaAnnotationName)) {
						String qualifiedName = usage.name().toString();
						if (collected.add(qualifiedName)) {
							BeanDefinition bean = new BeanDefinition(BeanDefinition.Kind.COMPONENT, qualifiedName,
									usage.simpleName());

							org.jboss.jandex.MethodInfo ctor = usage.firstMethod("<init>");
							if (ctor != null) {
								for (int i = 0; i < ctor.parametersCount(); i++) {
									bean.constructorParamTypes.add(ctor.parameterType(i).name().toString());
								}
							}

							for (org.jboss.jandex.Type iface : usage.interfaceTypes()) {
								bean.interfaceNames.add(iface.name().toString());
							}

							beans.add(bean);
						}
					}
				}
			}
		}

		return beans;
	}
}
