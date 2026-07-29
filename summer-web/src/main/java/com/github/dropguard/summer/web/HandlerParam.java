mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.web;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.RouteInfo.ParamBinding;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * Reflection-free description of a handler method parameter.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>Replaces {@code java.lang.reflect.Parameter} as the input contract for {@link
mport com.github.dropguard.summer.core.Internal;
@Internal
 * HttpParameterResolver}. Both DI engines build a {@code HandlerParam} from their own source of
mport com.github.dropguard.summer.core.Internal;
 * truth — the runtime engine from a reflective {@code Parameter}, the AOT engine from {@link
mport com.github.dropguard.summer.core.Internal;
 * com.github.dropguard.summer.core.bean.RouteInfo} metadata — so the resolver implementations stay
mport com.github.dropguard.summer.core.Internal;
 * engine-agnostic and reflection-free.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>The binding intent ({@link #binding()}) is the single source of truth shared by both engines;
mport com.github.dropguard.summer.core.Internal;
 * the concrete resolution strategy (runtime chain vs AOT inline code) differs per engine but reads
mport com.github.dropguard.summer.core.Internal;
 * from this same descriptor.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public interface HandlerParam {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /** The parameter's declared type. */
mport com.github.dropguard.summer.core.Internal;
    Class<?> type();
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * The binding name — the {@code @PathParam}/{@code @QueryParam} value, or the parameter name
mport com.github.dropguard.summer.core.Internal;
     * when no explicit value is given. Empty for parameters that do not bind to a named path/query
mport com.github.dropguard.summer.core.Internal;
     * segment.
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    String bindingName();
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /** The binding intent, shared by both engines via {@code RouteInfo.ParamBinding}. */
mport com.github.dropguard.summer.core.Internal;
    ParamBinding binding();
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /** Whether the parameter is annotated with {@code @Valid}. */
mport com.github.dropguard.summer.core.Internal;
    boolean validated();
mport com.github.dropguard.summer.core.Internal;
}
