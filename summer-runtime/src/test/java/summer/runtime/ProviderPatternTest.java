package summer.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import summer.core.Component;
import summer.core.Provider;

public class ProviderPatternTest {

	@Test
	public void testProviderPatternRegistersCorrectType() {
		var context = RuntimeBeanContainerBuilder.buildScoped(ProviderPatternTest.class);

		// Should be able to get String directly
		String provided = context.getBean(String.class);
		assertEquals("Hello Provider", provided);

		// Should also be able to get the Provider itself
		StringProvider provider = context.getBean(StringProvider.class);
		assertNotNull(provider);
		assertEquals("Hello Provider", provider.provide());
	}

	@Component
	public static class StringProvider implements Provider<String> {
		@Override
		public String provide() {
			return "Hello Provider";
		}
	}
}
