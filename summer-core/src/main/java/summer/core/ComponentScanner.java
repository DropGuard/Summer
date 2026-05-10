package summer.core;

import java.io.File;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

/**
 * Component scanner for finding and registering Summer components. Handles
 * classpath scanning for @Component and @RestController annotated classes.
 */
public class ComponentScanner {

	private final Set<Class<?>> componentClasses = new HashSet<>();

	/**
	 * Scans the given package for @Component and @RestController annotated classes.
	 */
	public void scan(String packageName) {
		scanComponents(packageName);
		scanSummerComponents();
	}

	private void scanComponents(String packageName) {
		String packagePath = packageName.replace('.', '/');
		try {
			Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(packagePath);
			while (resources.hasMoreElements()) {
				URL resource = resources.nextElement();
				File directory = new File(resource.toURI());
				if (directory.exists()) {
					scanDirectory(packageName, directory);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void scanDirectory(String packageName, File directory) {
		for (File file : directory.listFiles()) {
			if (file.isDirectory()) {
				scanDirectory(packageName + "." + file.getName(), file);
			} else if (file.getName().endsWith(".class")) {
				String className = packageName + "." + file.getName().substring(0, file.getName().length() - 6);
				try {
					Class<?> clazz = Class.forName(className);
					boolean isComponent = clazz.isAnnotationPresent(Component.class);
					boolean isRestController = java.util.Arrays.stream(clazz.getAnnotations())
							.anyMatch(a -> a.annotationType().getName().endsWith("RestController"));

					if (isComponent || isRestController) {
						componentClasses.add(clazz);
					}
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void scanSummerComponents() {
		// Register core Summer components
		try {
			System.out.println("Registering core Summer components...");

			// Web module components
			Class<?> routerClass = Class.forName("summer.web.Router");
			System.out.println("Found Router class: " + routerClass.getName());
			componentClasses.add(routerClass);

			Class<?> routerAdapterClass = Class.forName("summer.web.AnnotationRouterAdapter");
			System.out.println("Found AnnotationRouterAdapter class: " + routerAdapterClass.getName());
			componentClasses.add(routerAdapterClass);

			// Validation components
			Class<?> bodyValidatorClass = Class.forName("summer.validation.DefaultBodyValidator");
			System.out.println("Found DefaultBodyValidator class: " + bodyValidatorClass.getName());
			componentClasses.add(bodyValidatorClass);

			System.out.println("Core components registered successfully");

		} catch (ClassNotFoundException e) {
			System.err.println("Failed to register core Summer components:");
			e.printStackTrace();
		}
	}

	/**
	 * Adds a component class directly.
	 */
	public void registerComponent(Class<?> clazz) {
		if (!clazz.isAnnotationPresent(Component.class)) {
			throw new SummerException("Class " + clazz.getName() + " is not annotated with @Component");
		}
		componentClasses.add(clazz);
	}

	/**
	 * Gets all registered component classes.
	 */
	public Set<Class<?>> getComponentClasses() {
		return componentClasses;
	}
}