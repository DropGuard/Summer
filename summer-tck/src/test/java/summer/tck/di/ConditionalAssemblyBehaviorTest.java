package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.fixtures.di.ConditionalBean;
import summer.fixtures.di.TestMarker;
import summer.test.annotation.SummerTest;

@SummerTest(modules = "summer-tck-fixtures")
public class ConditionalAssemblyBehaviorTest {

	private final BeanContainer context;

	public ConditionalAssemblyBehaviorTest(BeanContainer context) {
		this.context = context;
	}

	@Test
	void conditionalBeanPresentWhenMarkerExists() {
		assertNotNull(context.getBean(TestMarker.class), "TestMarker should be registered");
		assertNotNull(context.getBean(ConditionalBean.class),
				"ConditionalBean should be registered when TestMarker exists");
	}
}
