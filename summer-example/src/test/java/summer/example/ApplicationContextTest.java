package summer.example;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.core.ApplicationContext;
import summer.web.Router;

public class ApplicationContextTest {

	@Test
	public void testApplicationContextInitialization() {
		try {
			// Initialize application context
			ApplicationContext context = ApplicationContext.scan("summer.example");

			// Verify all required beans are registered
			assertNotNull(context.getBean(UserRepository.class));
			assertNotNull(context.getBean(UserService.class));
			assertNotNull(context.getBean(UserController.class));
			assertNotNull(context.getBean(Router.class));

			System.out.println("ApplicationContext initialized successfully");

		} catch (Exception e) {
			e.printStackTrace();
			fail("ApplicationContext initialization failed: " + e.getMessage());
		}
	}
}