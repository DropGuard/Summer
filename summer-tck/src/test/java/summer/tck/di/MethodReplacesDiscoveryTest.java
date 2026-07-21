package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.List;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;
import summer.core.Discovery;
import summer.core.bean.BeanDefinition;
import summer.core.bean.BeanDeployment;
import summer.core.bean.SharedConditionEvaluator;
import summer.fixtures.di.MethodReplacesBean;
import summer.fixtures.di.MethodReplacesConfig;
import summer.fixtures.di.MethodReplacesReplacementConfig;

/**
 * Regression test for method-level {@code @Replaces} on {@code @Bean} methods.
 *
 * <p>
 * Two {@code @Bean} methods return the same type ({@code MethodReplacesBean});
 * exactly one carries {@code @Replaces(MethodReplacesBean.class)}. After
 * evaluation the original must be gone and the replacer must survive. This
 * guards against {@code Discovery} leaking a class-level {@code @Replaces} onto
 * the {@code @Bean} product (which once caused both products to be removed).
 */
class MethodReplacesDiscoveryTest {

	private static IndexView indexOf(Class<?>... classes) throws Exception {
		Indexer indexer = new Indexer();
		for (Class<?> c : classes) {
			String resource = "/" + c.getName().replace('.', '/') + ".class";
			try (InputStream is = c.getResourceAsStream(resource)) {
				if (is != null) {
					indexer.index(is);
				}
			}
		}
		return indexer.complete();
	}

	@Test
	void methodLevelReplacesKeepsReplacerAndDropsOriginal() throws Exception {
		IndexView index = indexOf(MethodReplacesConfig.class, MethodReplacesReplacementConfig.class,
				MethodReplacesBean.class);
		List<BeanDefinition> beans = Discovery.discover(BeanDeployment.forNarrow(index));

		new SharedConditionEvaluator().evaluate(beans);

		List<BeanDefinition> survivors = beans.stream()
				.filter(b -> "summer.fixtures.di.MethodReplacesBean".equals(b.qualifiedName)).toList();
		assertEquals(1, survivors.size(), "exactly one MethodReplacesBean bean should remain");
		assertNotNull(survivors.get(0).methodLevelReplaces, "the survivor must be the @Replaces-annotated replacer");
	}
}
