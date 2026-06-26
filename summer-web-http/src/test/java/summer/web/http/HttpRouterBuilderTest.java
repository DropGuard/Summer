package summer.web.http;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import summer.web.*;

class HttpRouterBuilderTest {

	@Test
	void shouldBuildSimpleRoutes() {
		AtomicReference<String> result = new AtomicReference<>();

		HttpRouter router = new HttpRouter.Builder(RadixTreeHttpRouter::new).get("/hello", ctx -> {
			result.set("hello");
		}).post("/world", ctx -> {
			result.set("world");
		}).build();

		router.route(ctx(HttpMethod.GET, "/hello"));
		assertEquals("hello", result.get());

		router.route(ctx(HttpMethod.POST, "/world"));
		assertEquals("world", result.get());
	}

	@Test
	void shouldBuildRoutesWithGroup() {
		AtomicReference<String> result = new AtomicReference<>();

		HttpRouter router = new HttpRouter.Builder(RadixTreeHttpRouter::new).group("/api/v1", v1 -> {
			v1.get("/users", ctx -> {
				result.set("list");
			});
			v1.get("/users/{id}", ctx -> {
				result.set("get:" + ctx.request().pathParam("id"));
			});
		}).build();

		router.route(ctx(HttpMethod.GET, "/api/v1/users"));
		assertEquals("list", result.get());

		router.route(ctx(HttpMethod.GET, "/api/v1/users/42"));
		assertEquals("get:42", result.get());
	}

	@Test
	void shouldBuildRoutesWithMount() {
		AtomicReference<String> result = new AtomicReference<>();

		HttpRouter router = new HttpRouter.Builder(RadixTreeHttpRouter::new).mount(r -> {
			r.get("/mounted", ctx -> {
				result.set("mounted");
			});
		}).build();

		router.route(ctx(HttpMethod.GET, "/mounted"));
		assertEquals("mounted", result.get());
	}

	@Test
	void shouldSupportFluentChaining() {
		HttpRouter.Builder builder = new HttpRouter.Builder(RadixTreeHttpRouter::new);

		HttpRouter.Builder result = builder.get("/a", ctx -> {
		}).post("/b", ctx -> {
		}).put("/c", ctx -> {
		}).delete("/d", ctx -> {
		});

		assertSame(builder, result);
	}

	@Test
	void shouldSupportNestedGroups() {
		AtomicReference<String> result = new AtomicReference<>();

		HttpRouter router = new HttpRouter.Builder(RadixTreeHttpRouter::new).group("/api", api -> {
			api.group("/v1", v1 -> {
				v1.get("/users", ctx -> {
					result.set("nested");
				});
			});
		}).build();

		router.route(ctx(HttpMethod.GET, "/api/v1/users"));
		assertEquals("nested", result.get());
	}

	@Test
	void shouldApplyGroupMiddleware() {
		AtomicReference<String> order = new AtomicReference<>("");

		Middleware auth = next -> ctx -> {
			order.set(order.get() + "auth:");
			next.handle(ctx);
		};

		HttpRouter router = new HttpRouter.Builder(RadixTreeHttpRouter::new).group("/api", api -> {
			api.use(auth);
			api.get("/protected", ctx -> {
				order.set(order.get() + "handler");
			});
		}).build();

		router.route(ctx(HttpMethod.GET, "/api/protected"));
		assertEquals("auth:handler", order.get());
	}

	private HttpContext ctx(HttpMethod method, String path) {
		Request req = new Request(method, path, null, null, new byte[0]);
		return new HttpContext(req);
	}
}
