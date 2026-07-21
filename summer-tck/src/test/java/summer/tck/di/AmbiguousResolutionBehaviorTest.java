package summer.tck.di;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import summer.core.BeanContainer;
import summer.tck.fixtures.di.errors.AmbiguousService;
import summer.tck.fixtures.di.errors.AmbiguousServiceImplOne;
import summer.tck.fixtures.di.errors.AmbiguousServiceImplTwo;
import summer.test.annotation.DualEngine;
import summer.test.annotation.SummerTest;

/**
 * Dual-engine (Runtime + AOT) contract: resolving a type with two
 * {@code @Component} implementations and no disambiguation.
 *
 * <p>
 * GAP (both engines): this SHOULD throw {@code AmbiguousBeanException}, but
 * neither engine enforces it today — {@code getBean(AmbiguousService.class)}
 * silently resolves one of the two implementations. This test locks the current
 * (broken) behaviour so the gap stays visible; when ambiguity enforcement is
 * added, the assertions here must change to expect
 * {@code AmbiguousBeanException} and this test will then fail, signalling the
 * fix is needed.
 * </p>
 */
@SummerTest(classes = {AmbiguousServiceImplOne.class, AmbiguousServiceImplTwo.class})
public class AmbiguousResolutionBehaviorTest {

	private final BeanContainer container;

	public AmbiguousResolutionBehaviorTest(BeanContainer container) {
		this.container = container;
	}

	@DualEngine
	void ambiguousResolutionResolvesSilently() {
		// GAP: should throw AmbiguousBeanException, but currently resolves one impl.
		AmbiguousService resolved = container.getBean(AmbiguousService.class);
		assertNotNull(resolved, "GAP: ambiguity is not enforced; one impl is resolved silently");
	}
}
