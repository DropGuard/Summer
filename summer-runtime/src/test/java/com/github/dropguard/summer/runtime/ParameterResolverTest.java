package com.github.dropguard.summer.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.web.HandlerParam;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpMethod;
import com.github.dropguard.summer.web.PathParamResolver;
import com.github.dropguard.summer.web.QueryParamResolver;
import com.github.dropguard.summer.web.Request;
import com.github.dropguard.summer.web.RequestAttributes;
import com.github.dropguard.summer.web.ThrowableResolver;
import com.github.dropguard.summer.web.TypeParameterResolver;
import com.github.dropguard.summer.web.annotation.PathParam;
import com.github.dropguard.summer.web.annotation.QueryParam;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the focused parameter resolvers that replaced {@code ReflectionParameterResolver}.
 */
class ParameterResolverTest {

    private HttpContext ctx(String path) {
        Request req = new Request(HttpMethod.GET, path, null, null, new byte[0]);
        return new HttpContext(req);
    }

    private HandlerParam param(String methodName, Class<?>... paramTypes)
            throws NoSuchMethodException {
        Method method = ParameterResolverTest.class.getDeclaredMethod(methodName, paramTypes);
        Parameter parameter = method.getParameters()[0];
        return new RuntimeHandlerParam(parameter);
    }

    // ── Fixture methods (used via reflection) ──────────────────────

    void typeContext(HttpContext ctx) {}

    void typeRequest(Request req) {}

    void pathParam(@PathParam("id") String id) {}

    void queryParam(@QueryParam("name") String name) {}

    void throwableParam(RuntimeException ex) {}

    // ── TypeParameterResolver ──────────────────────────────────────

    @Nested
    class TypeParameterResolverTest {

        private final TypeParameterResolver resolver = new TypeParameterResolver();

        @Test
        void supportsHttpContext() throws Exception {
            assertTrue(resolver.supports(param("typeContext", HttpContext.class)));
        }

        @Test
        void supportsRequest() throws Exception {
            assertTrue(resolver.supports(param("typeRequest", Request.class)));
        }

        @Test
        void rejectsString() throws Exception {
            assertFalse(resolver.supports(param("pathParam", String.class)));
        }

        @Test
        void resolvesHttpContext() throws Exception {
            HttpContext ctx = ctx("/test");
            assertSame(ctx, resolver.resolve(ctx, param("typeContext", HttpContext.class)));
        }

        @Test
        void resolvesRequest() throws Exception {
            HttpContext ctx = ctx("/test");
            assertSame(ctx.request(), resolver.resolve(ctx, param("typeRequest", Request.class)));
        }
    }

    // ── PathParamResolver ──────────────────────────────────────────

    @Nested
    class PathParamResolverTest {

        private final PathParamResolver resolver = new PathParamResolver();

        @Test
        void supportsAnnotatedParam() throws Exception {
            assertTrue(resolver.supports(param("pathParam", String.class)));
        }

        @Test
        void rejectsUnannotatedParam() throws Exception {
            assertFalse(resolver.supports(param("typeContext", HttpContext.class)));
        }

        @Test
        void resolvesPathParameter() throws Exception {
            HttpContext ctx = ctx("/users/42");
            ctx.request().setPathParam("id", "42");
            assertEquals("42", resolver.resolve(ctx, param("pathParam", String.class)));
        }
    }

    // ── QueryParamResolver ─────────────────────────────────────────

    @Nested
    class QueryParamResolverTest {

        private final QueryParamResolver resolver = new QueryParamResolver();

        @Test
        void supportsAnnotatedParam() throws Exception {
            assertTrue(resolver.supports(param("queryParam", String.class)));
        }

        @Test
        void rejectsUnannotatedParam() throws Exception {
            assertFalse(resolver.supports(param("typeContext", HttpContext.class)));
        }
    }

    // ── ThrowableResolver ──────────────────────────────────────────

    @Nested
    class ThrowableResolverTest {

        private final ThrowableResolver resolver = new ThrowableResolver();

        @Test
        void supportsRuntimeException() throws Exception {
            assertTrue(resolver.supports(param("throwableParam", RuntimeException.class)));
        }

        @Test
        void rejectsStringParam() throws Exception {
            assertFalse(resolver.supports(param("pathParam", String.class)));
        }

        @Test
        void resolvesExceptionFromAttribute() throws Exception {
            HttpContext ctx = ctx("/test");
            RuntimeException ex = new RuntimeException("boom");
            ctx.request().setAttribute(RequestAttributes.LAST_EXCEPTION, ex);
            assertSame(ex, resolver.resolve(ctx, param("throwableParam", RuntimeException.class)));
        }
    }
}
