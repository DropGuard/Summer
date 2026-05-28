package summer.scanner.runtime;

import summer.core.ApplicationContext;
import summer.core.DiEngine;

/**
 * Runtime DI engine that discovers beans via classpath scanning and reflection.
 * Scans the entry point's package plus framework packages declared via SPI.
 */
public class RuntimeDiEngine implements DiEngine {
	@Override
	public ApplicationContext create(Class<?> entryPoint) {
		return new RuntimeApplicationContext().scan(entryPoint.getPackageName());
	}
}
