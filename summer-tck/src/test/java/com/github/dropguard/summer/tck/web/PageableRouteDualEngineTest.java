package com.github.dropguard.summer.tck.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpMethod;
import com.github.dropguard.summer.web.Request;
import java.nio.charset.StandardCharsets;

/**
 * End-to-end pageable-param routing on BOTH engines.
 *
 * <p>On AOT the {@code @Pageable} branch of the generated route adapter resolves through the {@code
 * HttpParameterResolverChain} bean (provisioned engine-agnostically by {@code
 * HttpParameterResolverConfiguration}); on Runtime it resolves through the same chain. This test is
 * the fixture that closes the pageable-on-AOT verification gap — the generated adapter must
 * register the route and the chain must resolve the {@code DefaultPageRequest} from query params.
 */
@SummerTest
public class PageableRouteDualEngineTest extends AbstractWebRouteTCK {

    public PageableRouteDualEngineTest(BeanContainer context) {
        super(context);
    }

    @DualEngine
    protected void pageableParamResolvesThroughChainOnBothEngines() {
        // query is a separate Request field: the path stays clean for router matching.
        Request req = new Request(HttpMethod.GET, "/rt/items", "page=2&size=10", null, null);
        HttpContext ctx = new HttpContext(req);
        router.route(ctx);
        assertEquals(
                "page:2:size:10",
                new String(ctx.body(), StandardCharsets.UTF_8),
                "the @Pageable param must resolve through the chain on both engines");
    }
}
