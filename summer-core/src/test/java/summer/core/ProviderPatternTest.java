package summer.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class ProviderPatternTest {

    @Test
    public void testProviderPatternRegistersCorrectType() {
        ApplicationContext context = new ApplicationContext();
        context.registerComponent(StringProvider.class);
        context.initializeBeans();
        
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
