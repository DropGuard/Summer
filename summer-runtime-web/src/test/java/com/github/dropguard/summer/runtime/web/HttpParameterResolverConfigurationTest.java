package com.github.dropguard.summer.runtime.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.core.config.PageableProperties;
import com.github.dropguard.summer.web.DefaultPageRequest;
import com.github.dropguard.summer.web.DefaultPageResolver;
import com.github.dropguard.summer.web.HandlerParam;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpMethod;
import com.github.dropguard.summer.web.HttpParameterResolver;
import com.github.dropguard.summer.web.HttpParameterResolverChain;
import com.github.dropguard.summer.web.Request;
import com.github.dropguard.summer.web.RouteInfoHandlerParam;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Resolver-chain assembly (the {@code @Replaces} contract): the chain is built from the injected
 * resolver beans sorted by built-in priority — a {@code @Replaces}'d built-in is absent from the
 * injected list and must NOT be resynthesized (the pre-fix code constructed fresh built-in
 * instances inline, so a replacement never won). The container-level removal side is covered by
 * {@code ReplacesNegativeBehaviorTest} (narrow universe, negative-fixtures module).
 */
class HttpParameterResolverConfigurationTest {

    private final HttpParameterResolverConfiguration config =
            new HttpParameterResolverConfiguration();

    private static PageableProperties pageableProperties() {
        return new PageableProperties() {
            @Override
            public Integer defaultPage() {
                return 0;
            }

            @Override
            public Integer defaultSize() {
                return 20;
            }
        };
    }

    /** Claims the same type the replaced built-in owned. Plain class — never a bean, no leak. */
    static final class CustomPageResolver implements HttpParameterResolver {

        boolean invoked;

        @Override
        public boolean supports(HandlerParam param) {
            return DefaultPageRequest.class.equals(param.type());
        }

        @Override
        public Object resolve(HttpContext ctx, HandlerParam param) {
            invoked = true;
            return "CUSTOM";
        }
    }

    private static HttpContext context() {
        return new HttpContext(new Request(HttpMethod.GET, "/x", null, null, null));
    }

    private static HandlerParam pageableParam() {
        return new RouteInfoHandlerParam(DefaultPageRequest.class, "pageable", null, false);
    }

    @Test
    void replacedBuiltInIsNotResynthesized() {
        // The post-@Replaces state: the replaced DefaultPageResolver is absent from the injected
        // list. The chain must resolve through the replacement, not a freshly-built built-in.
        CustomPageResolver custom = new CustomPageResolver();
        HttpParameterResolverChain chain = config.resolverChain(List.of(custom));

        assertEquals("CUSTOM", chain.resolve(context(), pageableParam()));
        assertTrue(custom.invoked, "the replacement must handle DefaultPageRequest");
    }

    @Test
    void builtInOutranksUserResolver() {
        // With the built-in present, it wins: priority order, not input order (custom is first).
        CustomPageResolver custom = new CustomPageResolver();
        HttpParameterResolverChain chain =
                config.resolverChain(
                        List.of(custom, new DefaultPageResolver(pageableProperties())));

        assertInstanceOf(DefaultPageRequest.class, chain.resolve(context(), pageableParam()));
        assertFalse(
                custom.invoked, "the built-in (priority 1) must claim before the user resolver");
    }
}
