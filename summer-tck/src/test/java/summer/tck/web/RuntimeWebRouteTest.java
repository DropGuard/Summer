package summer.tck.web;

import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.test.annotation.DualEngine;
import summer.test.annotation.SummerTest;

/**
 * Web routing TCK.
 *
 * <p>
 * Routing behaviour is verified on BOTH DI engines via {@link DualEngine} (the
 * framework-enforced parity guarantee) — every case from
 * {@link AbstractWebRouteTCK#routeTestCases()} is exercised on Runtime and AOT.
 * The previous {@code AotWebRouteTest} sibling, which only switched the engine
 * through {@code createContext()}, is obsolete. Exception-handler behaviour
 * does not depend on engine wiring, so a plain {@code @Test} (Runtime) is
 * sufficient.
 * </p>
 */
@SummerTest
public class RuntimeWebRouteTest extends AbstractWebRouteTCK {

	public RuntimeWebRouteTest(BeanContainer context) {
		super(context);
	}

	@DualEngine
	protected void routingIsIdenticalAcrossEngines() {
		super.routeBehaviour();
	}

	@Test
	protected void exceptionHandlerBehaviour() {
		super.exceptionHandlerBehaviour();
	}
}
