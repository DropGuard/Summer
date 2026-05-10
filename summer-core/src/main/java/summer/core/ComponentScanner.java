package summer.core;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import java.util.HashSet;
import java.util.Set;

/**
 * Component scanner for finding and registering Summer components. Handles
 * classpath scanning for @Component and @RestController annotated classes
 * using ClassGraph for robust JAR and file-based scanning.
 */
public class ComponentScanner {

	private final Set<Class<?>> componentClasses = new HashSet<>();

	/**
	 * Scans the given package for @Component and @RestController annotated classes.
	 */
	public void scan(String packageName) {
		try (ScanResult scanResult = new ClassGraph()
				.enableAllInfo()
				.acceptPackages(packageName)
				.scan()) {
			
			// Find classes with @Component annotation
			componentClasses.addAll(scanResult.getClassesWithAnnotation(Component.class.getName()).loadClasses());
			
			// Find classes with any annotation ending in "RestController" (handles summer.web.annotation.RestController)
			componentClasses.addAll(scanResult.getClassesWithAnnotation("summer.web.annotation.RestController").loadClasses());
		}
		scanSummerComponents();
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