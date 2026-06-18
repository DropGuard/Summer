package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.tck.AbstractContextTCK;
import summer.fixtures.di.generic.GenericService;
import summer.fixtures.di.generic.GenericServiceClient;
import summer.fixtures.di.generic.StringServiceImpl;

/**
 * TCK test for generic interface dependency resolution.
 *
 * <p>
 * Tests whether the DI container can resolve {@code GenericService<String>} to
 * {@code StringServiceImpl} when {@code StringServiceImpl} implements
 * {@code GenericService<String>}.
 *
 * <p>
 * This is a key test for verifying that AOT and Runtime have consistent
 * behavior with generic interfaces.
 */
public abstract class AbstractGenericInterfaceTCK extends AbstractContextTCK {

	@Test
	void testContextStartsSuccessfully() {
		assertNotNull(context(), "BeanContainer should not be null");
	}

	@Test
	void testCanResolveGenericService() {
		BeanContainer ctx = context();
		// Try to resolve by raw type
		GenericService<?> service = ctx.getBean(GenericService.class);
		assertNotNull(service, "Should be able to resolve GenericService (raw type)");
		assertInstanceOf(StringServiceImpl.class, service, "GenericService should be resolved to StringServiceImpl");
	}

	@Test
	void testCanResolveStringServiceImpl() {
		BeanContainer ctx = context();
		StringServiceImpl service = ctx.getBean(StringServiceImpl.class);
		assertNotNull(service, "Should be able to resolve StringServiceImpl");
	}

	@Test
	void testSingletonConsistency() {
		BeanContainer ctx = context();
		GenericService<?> genericService = ctx.getBean(GenericService.class);
		StringServiceImpl stringService = ctx.getBean(StringServiceImpl.class);
		assertSame(genericService, stringService,
				"GenericService and StringServiceImpl should resolve to the same singleton instance");
	}

	@Test
	void testDependencyInjectionWithGenericInterface() {
		BeanContainer ctx = context();
		GenericServiceClient client = ctx.getBean(GenericServiceClient.class);
		assertNotNull(client, "GenericServiceClient should be instantiated");
		assertNotNull(client.getService(), "GenericServiceClient should have GenericService injected");
		assertInstanceOf(StringServiceImpl.class, client.getService(),
				"Injected GenericService should be StringServiceImpl");
	}
}
