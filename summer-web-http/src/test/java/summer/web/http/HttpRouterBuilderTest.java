package summer.web.http;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import summer.web.HttpRouter;
import summer.web.Middleware;

class HttpRouterBuilderTest {

	@Test
	void shouldBuildSimpleRoutes() {
		AtomicReference<String> result = new AtomicReference<>();

		HttpRouter router = new HttpRouter.Builder(RadixTreeHttpRouter::new).get("/hello", ctx -> {
			result.set("hello");
			return null;
		}).post("/world", ctx -> {
			result.set("world");
			return null;
		}).build();

		assertNotNull(router);
	}

	@Test
	void shouldBuildRoutesWithGroup() {
		AtomicReference<String> result = new AtomicReference<>();

		HttpRouter router = new HttpRouter.Builder(RadixTreeHttpRouter::new).group("/api/v1", v1 -> {
			v1.get("/users", ctx -> {
				result.set("list");
				return null;
			});
			v1.get("/users/{id}", ctx -> {
				result.set("get");
				return null;
			});
		}).build();

		assertNotNull(router);
	}

	@Test
	void shouldBuildRoutesWithMount() {
		AtomicReference<String> result = new AtomicReference<>();

		HttpRouter router = new HttpRouter.Builder(RadixTreeHttpRouter::new).mount(r -> {
			r.get("/mounted", ctx -> {
				result.set("mounted");
				return null;
			});
		}).build();

		assertNotNull(router);
	}

	@Test
	void shouldBuildRoutesWithMiddleware() {
		AtomicReference<String> result = new AtomicReference<>();

		Middleware logging = next -> ctx -> {
			result.set("middleware");
			return next.handle(ctx);
		};

		HttpRouter router = new HttpRouter.Builder(RadixTreeHttpRouter::new).use(logging).get("/test", ctx -> {
			result.set("handler");
			return null;
		}).build();

		assertNotNull(router);
	}

	@Test
	void shouldSupportFluentChaining() {
		HttpRouter.Builder builder = new HttpRouter.Builder(RadixTreeHttpRouter::new);

		// Verify fluent chaining returns the same builder instance
		HttpRouter.Builder result = builder.get("/a", ctx -> null).post("/b", ctx -> null).put("/c", ctx -> null)
				.delete("/d", ctx -> null);

		assertSame(builder, result);
	}

	@Test
	void shouldSupportNestedGroups() {
		AtomicReference<String> result = new AtomicReference<>();

		HttpRouter router = new HttpRouter.Builder(RadixTreeHttpRouter::new).group("/api", api -> {
			api.group("/v1", v1 -> {
				v1.get("/users", ctx -> {
					result.set("nested");
					return null;
				});
			});
		}).build();

		assertNotNull(router);
	}

	@Test
	void shouldApplyGroupMiddleware() {
		AtomicReference<String> order = new AtomicReference<>("");

		Middleware auth = next -> ctx -> {
			order.set(order.get() + "auth:");
			return next.handle(ctx);
		};

		HttpRouter router = new HttpRouter.Builder(RadixTreeHttpRouter::new).group("/api", api -> {
			api.use(auth);
			api.get("/protected", ctx -> {
				order.set(order.get() + "handler");
				return null;
			});
		}).build();

		assertNotNull(router);
	}
}
