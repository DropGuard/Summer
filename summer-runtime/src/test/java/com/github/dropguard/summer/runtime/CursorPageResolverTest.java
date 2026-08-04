package com.github.dropguard.summer.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.config.ConfigBinder.BindingContext;
import com.github.dropguard.summer.core.config.PageableProperties;
import com.github.dropguard.summer.web.CursorPageResolver;
import com.github.dropguard.summer.web.CursorPageable;
import com.github.dropguard.summer.web.HandlerParam;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpMethod;
import com.github.dropguard.summer.web.HttpParameterResolver;
import com.github.dropguard.summer.web.Request;
import java.lang.reflect.Parameter;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CursorPageResolverTest {

    @BeforeAll
    static void installInterfaceBinder() {
        // Interface config binding (@ConfigMapping) is provided by the runtime module.
    }

    private final CursorPageResolver resolver = new CursorPageResolver(pageableProps(0, 20));

    private static PageableProperties pageableProps(int defaultPage, int defaultSize) {
        return new RuntimeConfigBinder()
                .bind(
                        BindingContext.of(
                                Map.of(
                                        "pageable.defaultPage", defaultPage,
                                        "pageable.defaultSize", defaultSize)),
                        "pageable",
                        PageableProperties.class);
    }

    private HttpContext ctx(String query) {
        Request req = new Request(HttpMethod.GET, "/test", query, null, new byte[0]);
        return new HttpContext(req);
    }

    private HandlerParam cursorParam() throws Exception {
        Parameter param =
                TestController.class.getDeclaredMethod("withCursor", CursorPageable.class)
                        .getParameters()[0];
        return new RuntimeHandlerParam(param);
    }

    private HandlerParam stringParam() throws Exception {
        Parameter param =
                TestController.class.getDeclaredMethod("withString", String.class)
                        .getParameters()[0];
        return new RuntimeHandlerParam(param);
    }

    @Test
    void shouldResolveWithExplicitParams() throws Exception {
        HttpContext ctx = ctx("cursor=100&limit=50");
        CursorPageable pageable = (CursorPageable) resolver.resolve(ctx, cursorParam());
        assertEquals(100L, pageable.cursor());
        assertEquals(50, pageable.limit());
    }

    @Test
    void shouldDefaultLimitAndNullCursorWhenNoParams() throws Exception {
        HttpContext ctx = ctx(null);
        CursorPageable pageable = (CursorPageable) resolver.resolve(ctx, cursorParam());
        assertNull(pageable.cursor());
        assertEquals(20, pageable.limit());
    }

    @Test
    void shouldUseCustomDefaults() throws Exception {
        HttpParameterResolver customResolver = new CursorPageResolver(pageableProps(1, 10));
        HttpContext ctx = ctx(null);
        CursorPageable pageable = (CursorPageable) customResolver.resolve(ctx, cursorParam());
        assertNull(pageable.cursor());
        assertEquals(10, pageable.limit());
    }

    @Test
    void shouldClampNegativeLimitToZero() throws Exception {
        HttpContext ctx = ctx("limit=-10");
        CursorPageable pageable = (CursorPageable) resolver.resolve(ctx, cursorParam());
        assertEquals(0, pageable.limit());
    }

    @Test
    void shouldTreatNegativeCursorAsAbsent() throws Exception {
        HttpContext ctx = ctx("cursor=-5");
        CursorPageable pageable = (CursorPageable) resolver.resolve(ctx, cursorParam());
        assertNull(pageable.cursor());
    }

    @Test
    void shouldTreatUnparsableCursorAsAbsent() throws Exception {
        HttpContext ctx = ctx("cursor=not-a-number");
        CursorPageable pageable = (CursorPageable) resolver.resolve(ctx, cursorParam());
        assertNull(pageable.cursor());
    }

    @Test
    void shouldSupportCursorParameter() throws Exception {
        assertTrue(resolver.supports(cursorParam()));
    }

    @Test
    void shouldNotSupportStringParameter() throws Exception {
        assertFalse(resolver.supports(stringParam()));
    }

    // Test controller for parameter reflection
    static class TestController {
        public void withCursor(CursorPageable pageable) {}

        public void withString(String str) {}
    }
}
