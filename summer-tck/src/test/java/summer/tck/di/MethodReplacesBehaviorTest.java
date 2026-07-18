package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.fixtures.di.MethodReplacesBean;
import summer.test.annotation.DualEngine;
import summer.test.annotation.SummerTest;

@SummerTest
public class MethodReplacesBehaviorTest {

	private final BeanContainer context;

	public MethodReplacesBehaviorTest(BeanContainer context) {
		this.context = context;
	}

	@DualEngine
	void methodLevelReplacesReplacesByReturnType() {
		MethodReplacesBean bean = context.getBean(MethodReplacesBean.class);
		assertNotNull(bean, "MethodReplacesBean should be registered");
		assertEquals("replaced", bean.getValue(), "MethodReplacesReplacementConfig should replace the bean");
	}
}
