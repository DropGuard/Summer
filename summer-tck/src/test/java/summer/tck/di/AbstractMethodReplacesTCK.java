package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.fixtures.di.MethodReplacesBean;
import summer.tck.AbstractContextTCK;

/**
 * TCK for method-level {@code @Replaces} assembly logic.
 *
 * <p>
 * Verifies that:
 * </p>
 * <ul>
 * <li>Method-level @Replaces replaces a bean by return type</li>
 * <li>The replacement bean is used instead of the original</li>
 * </ul>
 */
public abstract class AbstractMethodReplacesTCK extends AbstractContextTCK {

	@Test
	void methodLevelReplacesReplacesByReturnType() {
		BeanContainer ctx = context();
		MethodReplacesBean bean = ctx.getBean(MethodReplacesBean.class);
		assertNotNull(bean, "MethodReplacesBean should be registered");
		assertEquals("replaced", bean.getValue(), "MethodReplacesReplacementConfig should replace the bean");
	}
}
