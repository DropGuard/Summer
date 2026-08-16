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
 * Typed PATH/QUERY param conversion on BOTH engines.
 *
 * <p>Regression for the conversion divergence: the runtime resolvers and the AOT generated adapter
 * must convert identically — enums (case-insensitive), float/short/byte/char, booleans. Previously
 * an enum path param was passed through as a String on Runtime (ClassCastException at the handler),
 * float/short/byte/char query params threw on Runtime, and an enum query param broke the AOT build.
 * Both engines now share {@code TypeConverter} as the single conversion truth.
 */
@SummerTest
public class TypedParamDualEngineTest extends AbstractWebRouteTCK {

    public TypedParamDualEngineTest(BeanContainer context) {
        super(context);
    }

    private String dispatch(String path, String query) throws Exception {
        Request req = new Request(HttpMethod.GET, path, query, null, null);
        HttpContext ctx = new HttpContext(req);
        router.route(ctx);
        return new String(ctx.body(), StandardCharsets.UTF_8);
    }

    @DualEngine
    protected void typedParamsConvertIdenticallyOnBothEngines() throws Exception {
        // Enum path param, lowercase input -> case-insensitive enum coercion.
        assertEquals("color:RED", dispatch("/rt/color/red", null));
        assertEquals("color:GREEN", dispatch("/rt/color/GREEN", null));

        // float / short / boolean / char + lowercase enum query params.
        assertEquals(
                "typed:1.5:3:true:x:GREEN",
                dispatch("/rt/typed", "ratio=1.5&size=3&flag=true&letter=x&hue=green"));
    }
}
