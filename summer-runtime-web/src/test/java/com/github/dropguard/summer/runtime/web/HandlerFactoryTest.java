package com.github.dropguard.summer.runtime.web;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.HandlerParam;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpMethod;
import com.github.dropguard.summer.web.HttpParameterResolver;
import com.github.dropguard.summer.web.HttpParameterResolverChain;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.Request;
import com.github.dropguard.summer.web.TypeParameterResolver;
import com.github.dropguard.summer.web.annotation.QueryParam;
import com.github.dropguard.summer.web.exception.HandlerInvocationException;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link HandlerFactory}.
 *
 * <p>Tests handler creation, parameter resolution, and exception handling.
 */
class HandlerFactoryTest {

    @Test
    void shouldCreateHandlerAndInvokeMethod() throws Exception {
        TestController controller = new TestController();
        Method method =
                TestController.class.getDeclaredMethod("hello", String.class, HttpContext.class);
        HttpParameterResolverChain chain =
                new HttpParameterResolverChain(
                        List.of(new TypeParameterResolver(), new TestResolver("World")));

        Handler handler = HandlerFactory.create(controller, method, chain);
        Request request = new Request(HttpMethod.GET, "/hello", null, null, null);
        HttpContext ctx = new HttpContext(request);

        handler.handle(ctx);
        assertEquals(
                "Hello, World", new String(ctx.body(), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void shouldRethrowRuntimeExceptionDirectly() throws Exception {
        TestController controller = new TestController();
        Method method = TestController.class.getDeclaredMethod("throwRuntime");
        HttpParameterResolverChain chain = new HttpParameterResolverChain(List.of());

        Handler handler = HandlerFactory.create(controller, method, chain);
        Request request = new Request(HttpMethod.GET, "/error", null, null, null);
        HttpContext ctx = new HttpContext(request);

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> handler.handle(ctx));
        assertEquals("bad argument", ex.getMessage());
    }

    @Test
    void shouldPropagateCheckedExceptionUnwrapped() throws Exception {
        TestController controller = new TestController();
        Method method = TestController.class.getDeclaredMethod("throwChecked");
        HttpParameterResolverChain chain = new HttpParameterResolverChain(List.of());

        Handler handler = HandlerFactory.create(controller, method, chain);
        Request request = new Request(HttpMethod.GET, "/error", null, null, null);
        HttpContext ctx = new HttpContext(request);

        // Handler.handle declares throws Exception: a checked exception from the controller
        // propagates unwrapped so @ExceptionHandler matching sees the original exception.
        Exception ex =
                assertThrows(
                        Exception.class,
                        () -> handler.handle(ctx),
                        "a checked exception propagates unwrapped through Handler.handle");
        assertEquals("checked exception", ex.getMessage());
        assertFalse(ex instanceof HandlerInvocationException);
    }

    @Test
    void shouldResolveMultipleParameters() throws Exception {
        TestController controller = new TestController();
        Method method =
                TestController.class.getDeclaredMethod(
                        "greet", String.class, String.class, HttpContext.class);
        HttpParameterResolverChain chain =
                new HttpParameterResolverChain(
                        List.of(
                                new TypeParameterResolver(),
                                new BindingResolver("greeting", "Hello"),
                                new BindingResolver("name", "Alice")));

        Handler handler = HandlerFactory.create(controller, method, chain);
        Request request = new Request(HttpMethod.GET, "/greet", null, null, null);
        HttpContext ctx = new HttpContext(request);

        handler.handle(ctx);
        assertEquals(
                "Hello, Alice", new String(ctx.body(), java.nio.charset.StandardCharsets.UTF_8));
    }

    // Test controller
    static class TestController {
        void hello(String name, HttpContext ctx) {
            ctx.text(HttpStatus.OK, "Hello, " + name);
        }

        void greet(
                @QueryParam("greeting") String greeting,
                @QueryParam("name") String name,
                HttpContext ctx) {
            ctx.text(HttpStatus.OK, greeting + ", " + name);
        }

        void throwRuntime() {
            throw new IllegalArgumentException("bad argument");
        }

        void throwChecked() throws Exception {
            throw new Exception("checked exception");
        }
    }

    // Test resolver that always resolves to a fixed value.
    static class TestResolver implements HttpParameterResolver {
        private final Object value;

        TestResolver(Object value) {
            this.value = value;
        }

        @Override
        public boolean supports(HandlerParam param) {
            return true;
        }

        @Override
        public Object resolve(HttpContext ctx, HandlerParam param) {
            return value;
        }
    }

    // Test resolver that resolves a parameter by its binding name.
    static class BindingResolver implements HttpParameterResolver {
        private final String bindingName;
        private final Object value;

        BindingResolver(String bindingName, Object value) {
            this.bindingName = bindingName;
            this.value = value;
        }

        @Override
        public boolean supports(HandlerParam param) {
            return param.bindingName().equals(bindingName);
        }

        @Override
        public Object resolve(HttpContext ctx, HandlerParam param) {
            return value;
        }
    }
}
