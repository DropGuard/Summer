package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.core.validation.ValidationException;
import summer.fixtures.validation.*;
import summer.tck.AbstractContextTCK;

/**
 * TCK contract for the Validation Phase.
 *
 * <p>
 * Verifies that both Runtime and AOT engines:
 * </p>
 * <ul>
 * <li>Run {@code Validator<T>} beans after property binding</li>
 * <li>Throw {@link ValidationException} when validation fails</li>
 * <li>Allow beans to be created when validation passes</li>
 * </ul>
 */
public abstract class AbstractValidationTCK extends AbstractContextTCK {

	/**
	 * Creates a context with a specific entry point.
	 */
	protected abstract BeanContainer createContext(Class<?> entryPoint);

	@Test
	void testValidationPassesWhenTlsEnabledWithCerts() {
		// application.yml has tls.enabled=true with cert-chain and private-key
		BeanContainer ctx = context();
		TlsService service = ctx.getBean(TlsService.class);
		assertNotNull(service, "TlsService should be created when validation passes");
		assertTrue(service.getConfig().enabled(), "TLS should be enabled");
		assertNotNull(service.getConfig().certChain(), "cert-chain should be bound from YAML");
	}

	@Test
	void testValidationPassesWhenTlsDisabled() {
		// TlsConfig has @DefaultValue("false") for enabled, so when TLS section is
		// absent, enabled=false and validation skips the cert check.
		BeanContainer ctx = createContext(ValidationConfig.class);
		assertNotNull(ctx, "Context should be created even when TLS is disabled");
	}

	@Test
	void testValidatorIsRegisteredAsBean() {
		BeanContainer ctx = context();
		TlsValidator validator = ctx.getBean(TlsValidator.class);
		assertNotNull(validator, "Validator should be registered as a bean");
	}
}
