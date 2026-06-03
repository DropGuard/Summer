package summer.runtime;

import summer.core.ApplicationContext;
import summer.core.DiEngine;
import summer.core.RuntimeDiMarker;

/**
 * Runtime DI engine that discovers beans via classpath scanning and reflection.
 * Scans the entry point's package plus framework packages declared via SPI.
 */
public class RuntimeDiEngine implements DiEngine {
	@Override
	public ApplicationContext create(Class<?> entryPoint) {
		RuntimeApplicationContext ctx = new RuntimeApplicationContext();
		ctx.registerComponent(RuntimeDiMarker.class);
		return ctx.scan(entryPoint.getPackageName());
	}
}
