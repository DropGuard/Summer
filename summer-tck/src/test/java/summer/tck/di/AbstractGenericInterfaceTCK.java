package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import summer.core.ApplicationContext;
import summer.tck.di.generic.GenericService;
import summer.tck.di.generic.GenericServiceClient;
import summer.tck.di.generic.StringServiceImpl;

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
public abstract class AbstractGenericInterfaceTCK {

	protected ApplicationContext context;

	protected abstract ApplicationContext createAndInitializeContext();

	protected ApplicationContext getContext() {
		if (context == null) {
			context = createAndInitializeContext();
		}
		return context;
	}

	@AfterEach
	void tearDown() {
		if (context != null) {
			context.destroy();
			context = null;
		}
	}

	@Test
	void testContextStartsSuccessfully() {
		assertNotNull(getContext(), "ApplicationContext should not be null");
	}

	@Test
	void testCanResolveGenericService() {
		ApplicationContext ctx = getContext();
		// Try to resolve by raw type
		GenericService<?> service = ctx.getBean(GenericService.class);
		assertNotNull(service, "Should be able to resolve GenericService (raw type)");
		assertInstanceOf(StringServiceImpl.class, service, "GenericService should be resolved to StringServiceImpl");
	}

	@Test
	void testCanResolveStringServiceImpl() {
		ApplicationContext ctx = getContext();
		StringServiceImpl service = ctx.getBean(StringServiceImpl.class);
		assertNotNull(service, "Should be able to resolve StringServiceImpl");
	}

	@Test
	void testSingletonConsistency() {
		ApplicationContext ctx = getContext();
		GenericService<?> genericService = ctx.getBean(GenericService.class);
		StringServiceImpl stringService = ctx.getBean(StringServiceImpl.class);
		assertSame(genericService, stringService,
				"GenericService and StringServiceImpl should resolve to the same singleton instance");
	}

	@Test
	void testDependencyInjectionWithGenericInterface() {
		ApplicationContext ctx = getContext();
		GenericServiceClient client = ctx.getBean(GenericServiceClient.class);
		assertNotNull(client, "GenericServiceClient should be instantiated");
		assertNotNull(client.getService(), "GenericServiceClient should have GenericService injected");
		assertInstanceOf(StringServiceImpl.class, client.getService(),
				"Injected GenericService should be StringServiceImpl");
	}
}
