package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.core.ApplicationContext;
import summer.tck.AbstractContextTCK;
import summer.fixtures.di.inheritance.BaseService;
import summer.fixtures.di.inheritance.ExtendedService;
import summer.fixtures.di.inheritance.ServiceClient;
import summer.fixtures.di.inheritance.ServiceImpl;

/**
 * TCK test for interface inheritance dependency resolution.
 *
 * <p>
 * Tests whether the DI container can resolve {@code BaseService} to
 * {@code ServiceImpl} when {@code ServiceImpl} directly implements
 * {@code ExtendedService} (which extends {@code BaseService}).
 *
 * <p>
 * This is a key test for verifying that AOT and Runtime have consistent
 * dependency matching behavior.
 */
public abstract class AbstractInterfaceInheritanceTCK extends AbstractContextTCK {

	@Test
	void testContextStartsSuccessfully() {
		assertNotNull(context(), "ApplicationContext should not be null");
	}

	@Test
	void testCanResolveBaseService() {
		ApplicationContext ctx = context();
		BaseService baseService = ctx.getBean(BaseService.class);
		assertNotNull(baseService, "Should be able to resolve BaseService");
		assertInstanceOf(ServiceImpl.class, baseService, "BaseService should be resolved to ServiceImpl");
	}

	@Test
	void testCanResolveExtendedService() {
		ApplicationContext ctx = context();
		ExtendedService extendedService = ctx.getBean(ExtendedService.class);
		assertNotNull(extendedService, "Should be able to resolve ExtendedService");
		assertInstanceOf(ServiceImpl.class, extendedService, "ExtendedService should be resolved to ServiceImpl");
	}

	@Test
	void testSingletonConsistency() {
		ApplicationContext ctx = context();
		BaseService baseService = ctx.getBean(BaseService.class);
		ExtendedService extendedService = ctx.getBean(ExtendedService.class);
		assertSame(baseService, extendedService,
				"BaseService and ExtendedService should resolve to the same singleton instance");
	}

	@Test
	void testDependencyInjectionWithInheritedInterface() {
		ApplicationContext ctx = context();
		ServiceClient client = ctx.getBean(ServiceClient.class);
		assertNotNull(client, "ServiceClient should be instantiated");
		assertNotNull(client.getBaseService(), "ServiceClient should have BaseService injected");
		assertInstanceOf(ServiceImpl.class, client.getBaseService(), "Injected BaseService should be ServiceImpl");
	}
}
