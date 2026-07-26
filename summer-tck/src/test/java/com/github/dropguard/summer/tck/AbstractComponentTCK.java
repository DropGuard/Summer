package com.github.dropguard.summer.tck;

import org.junit.jupiter.api.AfterEach;

/**
 * Base class for TCK tests that test components directly (not via DI).
 *
 * <p>
 * Use for:
 * <ul>
 * <li>Router implementation tests</li>
 * <li>WebSocket router tests</li>
 * <li>JdbcTemplate tests</li>
 * <li>Other directly-instantiated components</li>
 * </ul>
 *
 * <p>
 * Usage:
 * 
 * <pre>
 * public abstract class AbstractRouterTCK extends AbstractComponentTCK {
 * 	protected abstract Function&lt;List&lt;Route&gt;, HttpRouter&gt; engineFactory();
 *
 * 	&#64;Test
 * 	void testRoute() {
 * 		HttpRouter r = builder().get("/users", ctx -> "ok").buildWith(engineFactory());
 * 		assertEquals("ok", r.route(ctx(HttpMethod.GET, "/users")));
 * 	}
 * }
 * </pre>
 */
public abstract class AbstractComponentTCK extends AbstractTCK {

	/**
	 * Hook for subclasses to clean up component resources.
	 *
	 * <p>
	 * Default is no-op. Override if your component needs explicit cleanup.
	 */
	protected void cleanupComponent() {
		// Default: no-op
	}

	@AfterEach
	void cleanup() {
		cleanupComponent();
	}
}
