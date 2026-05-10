package summer.validation.hv;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.core.ApplicationContext;
import summer.core.Component;
import summer.validation.BodyValidator;
import summer.web.Request;
import summer.web.Response;
import summer.web.annotation.Post;
import summer.web.annotation.RestController;

public class HibernateBodyValidatorIntegrationTest {

	@Test
	public void testValidationMiddlewareIntegration() {
		// 启动应用程序上下文并扫描组件
		ApplicationContext context = ApplicationContext.scan("summer.validation.hv");

		// 检查是否正确地找到了组件
		assertTrue(context.getComponentClasses().contains(HibernateBodyValidator.class));
		assertTrue(context.getComponentClasses().contains(HibernateValidationMiddleware.class));

		// 检查是否可以从上下文获取验证器
		BodyValidator validator = context.getBean(BodyValidator.class);
		assertNotNull(validator);
		assertTrue(validator instanceof HibernateBodyValidator);

		// 验证是否可以获取中间件
		HibernateValidationMiddleware middleware = context.getBean(HibernateValidationMiddleware.class);
		assertNotNull(middleware);
	}

	// 测试控制器
	@Component
	@RestController("/users")
	public static class UserController {

		@Post("/")
		public Response createUser(Request request) {
			// Under explicit API, validation happens explicitly inside the method body or
			// via explicit middleware
			UserRequest user = request.body(UserRequest.class);
			return new Response(null);
		}
	}

	// 测试请求类
	public static class UserRequest {

		@jakarta.validation.constraints.NotNull(message = "Name is required")
		@jakarta.validation.constraints.NotEmpty(message = "Name cannot be empty")
		@jakarta.validation.constraints.Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
		private String name;

		@jakarta.validation.constraints.Email(message = "Email must be valid")
		@jakarta.validation.constraints.NotNull(message = "Email is required")
		private String email;

		@jakarta.validation.constraints.Min(value = 18, message = "Age must be at least 18")
		@jakarta.validation.constraints.Max(value = 100, message = "Age must be at most 100")
		private int age;

		@jakarta.validation.constraints.Pattern(regexp = "\\d{3}-\\d{3}-\\d{4}", message = "Phone must be in format XXX-XXX-XXXX")
		private String phone;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getEmail() {
			return email;
		}

		public void setEmail(String email) {
			this.email = email;
		}

		public int getAge() {
			return age;
		}

		public void setAge(int age) {
			this.age = age;
		}

		public String getPhone() {
			return phone;
		}

		public void setPhone(String phone) {
			this.phone = phone;
		}
	}
}
