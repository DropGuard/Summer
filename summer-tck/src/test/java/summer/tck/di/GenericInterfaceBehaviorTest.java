package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.fixtures.di.generic.GenericService;
import summer.fixtures.di.generic.GenericServiceClient;
import summer.fixtures.di.generic.StringServiceImpl;
import summer.test.annotation.SummerTest;

@SummerTest
public class GenericInterfaceBehaviorTest {

	private final BeanContainer context;

	public GenericInterfaceBehaviorTest(BeanContainer context) {
		this.context = context;
	}

	@Test
	void testContextStartsSuccessfully() {
		assertNotNull(context, "BeanContainer should not be null");
	}

	@Test
	void testCanResolveGenericService() {
		GenericService<?> service = context.getBean(GenericService.class);
		assertNotNull(service, "Should be able to resolve GenericService (raw type)");
		assertInstanceOf(StringServiceImpl.class, service, "GenericService should be resolved to StringServiceImpl");
	}

	@Test
	void testCanResolveStringServiceImpl() {
		StringServiceImpl service = context.getBean(StringServiceImpl.class);
		assertNotNull(service, "Should be able to resolve StringServiceImpl");
	}

	@Test
	void testSingletonConsistency() {
		GenericService<?> genericService = context.getBean(GenericService.class);
		StringServiceImpl stringService = context.getBean(StringServiceImpl.class);
		assertSame(genericService, stringService,
				"GenericService and StringServiceImpl should resolve to the same singleton instance");
	}

	@Test
	void testDependencyInjectionWithGenericInterface() {
		GenericServiceClient client = context.getBean(GenericServiceClient.class);
		assertNotNull(client, "GenericServiceClient should be instantiated");
		assertNotNull(client.getService(), "GenericServiceClient should have GenericService injected");
		assertInstanceOf(StringServiceImpl.class, client.getService(),
				"Injected GenericService should be StringServiceImpl");
	}
}
