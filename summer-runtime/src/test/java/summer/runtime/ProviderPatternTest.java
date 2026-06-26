package summer.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.fixtures.StringProvider;

public class ProviderPatternTest {

	@Test
	public void testProviderPatternRegistersCorrectType() {
		BeanContainer context = RuntimeBeanContainerBuilder.buildFromSeeds(StringProvider.class);

		// Should be able to get String directly
		String provided = context.getBean(String.class);
		assertEquals("Hello Provider", provided);

		// Should also be able to get the Provider itself
		StringProvider provider = context.getBean(StringProvider.class);
		assertNotNull(provider);
		assertEquals("Hello Provider", provider.provide());
	}
}
