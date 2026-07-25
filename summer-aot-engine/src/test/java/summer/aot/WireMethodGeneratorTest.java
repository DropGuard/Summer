package summer.aot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.palantir.javapoet.CodeBlock;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import summer.core.bean.BeanDefinition;
import summer.core.bean.InjectionParameter;

/**
 * Verifies that {@link WireMethodGenerator}'s constructor-argument emission
 * reads each parameter's own resolved list directly — no positional cursor, no
 * re-filtering by element type. The key regression this locks in: two
 * {@code List<T>} parameters of the SAME element type must keep their own
 * dependency slices, which the old cursor+filter approach could not guarantee.
 */
class WireMethodGeneratorTest {

	private final WireMethodGenerator generator = new WireMethodGenerator();

	private CodeBlock buildArgs(BeanDefinition bean) throws Exception {
		Method m = WireMethodGenerator.class.getDeclaredMethod("buildConstructorArgs", BeanDefinition.class);
		m.setAccessible(true);
		return (CodeBlock) m.invoke(generator, bean);
	}

	private BeanDefinition dep(String name) {
		BeanDefinition d = new BeanDefinition(name, name.substring(name.lastIndexOf('.') + 1));
		d.variableName = Character.toLowerCase(d.simpleName.charAt(0)) + d.simpleName.substring(1);
		return d;
	}

	@Test
	void twoListsOfSameElementTypeKeepDistinctSlices() throws Exception {
		BeanDefinition svcA = dep("summer.fx.ServiceA");
		BeanDefinition svcB = dep("summer.fx.ServiceB");

		BeanDefinition consumer = new BeanDefinition("summer.fx.Consumer", "Consumer");
		// first List<Service> carries svcA, second List<Service> carries svcB
		InjectionParameter first = new InjectionParameter("java.util.List<summer.fx.Service>",
				new ArrayList<>(List.of(svcA)));
		InjectionParameter second = new InjectionParameter("java.util.List<summer.fx.Service>",
				new ArrayList<>(List.of(svcB)));
		consumer.parameters.add(first);
		consumer.parameters.add(second);

		String args = buildArgs(consumer).toString();
		// Each List emits its own slice: [svcA], [svcB] — not both lists getting
		// the union, which a by-element-type re-filter would produce.
		assertEquals("java.util.List.of(serviceA), java.util.List.of(serviceB)", args);
	}

	@Test
	void emptyListEmitsEmptyListOf() throws Exception {
		BeanDefinition consumer = new BeanDefinition("summer.fx.Consumer", "Consumer");
		consumer.parameters.add(new InjectionParameter("java.util.List<summer.fx.Service>", new ArrayList<>()));

		assertEquals("java.util.List.of()", buildArgs(consumer).toString());
	}

	@Test
	void beanContainerScalarEmitsNull() throws Exception {
		BeanDefinition consumer = new BeanDefinition("summer.fx.Consumer", "Consumer");
		consumer.parameters.add(new InjectionParameter("summer.core.BeanContainer", new ArrayList<>()));

		assertEquals("null", buildArgs(consumer).toString());
	}

	@Test
	void scalarDependencyEmitsVariableName() throws Exception {
		BeanDefinition depBean = dep("summer.fx.Dep");
		BeanDefinition consumer = new BeanDefinition("summer.fx.Consumer", "Consumer");
		consumer.parameters.add(new InjectionParameter("summer.fx.Dep", new ArrayList<>(List.of(depBean))));

		assertEquals("dep", buildArgs(consumer).toString());
	}
}
