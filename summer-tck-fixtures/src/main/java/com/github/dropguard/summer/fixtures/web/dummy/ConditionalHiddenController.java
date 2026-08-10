package com.github.dropguard.summer.fixtures.web.dummy;

import com.github.dropguard.summer.core.annotation.ConditionalOnBean;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.RestController;

/**
 * A controller whose {@code @ConditionalOnBean} is never satisfied. Its bean — and therefore its
 * routes — must be absent on BOTH engines (route collection runs after condition evaluation on
 * Runtime and AOT alike). Guards the conditional-route parity contract in {@code
 * ConditionalRouteParityTest}.
 */
@RestController("/hidden")
@ConditionalOnBean(ConditionalHiddenController.NeverPresentMarker.class)
public class ConditionalHiddenController {

    /** Marker type that no bean in any universe provides — the condition never holds. */
    public static class NeverPresentMarker {}

    @Get("")
    public void hidden(HttpContext ctx) {
        ctx.text(HttpStatus.OK, "must never be routed");
    }
}
