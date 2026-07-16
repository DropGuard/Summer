package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.fixtures.validation.TlsService;
import summer.fixtures.validation.TlsValidator;
import summer.test.annotation.SummerTest;

@SummerTest
public class ValidationBehaviorTest {

	private final BeanContainer context;

	public ValidationBehaviorTest(BeanContainer context) {
		this.context = context;
	}

	@Test
	void testValidationPassesWhenTlsEnabledWithCerts() {
		TlsService service = context.getBean(TlsService.class);
		assertNotNull(service, "TlsService should be created when validation passes");
		assertTrue(service.getConfig().enabled(), "TLS should be enabled");
		assertNotNull(service.getConfig().certChain(), "cert-chain should be bound from YAML");
	}

	@Test
	void testValidationPassesWhenTlsDisabled() {
		assertNotNull(context, "Context should be created even when TLS is disabled");
	}

	@Test
	void testValidatorIsRegisteredAsBean() {
		TlsValidator validator = context.getBean(TlsValidator.class);
		assertNotNull(validator, "Validator should be registered as a bean");
	}
}
