package com.github.dropguard.summer.web;

import com.github.dropguard.summer.core.bean.RouteInfo.ParamBinding;

/**
 * Reflection-free description of a handler method parameter.
 *
 * <p>
 * Replaces {@code java.lang.reflect.Parameter} as the input contract for
 * {@link HttpParameterResolver}. Both DI engines build a {@code HandlerParam}
 * from their own source of truth — the runtime engine from a reflective
 * {@code Parameter}, the AOT engine from
 * {@link com.github.dropguard.summer.core.bean.RouteInfo} metadata — so the
 * resolver implementations stay engine-agnostic and reflection-free.
 * </p>
 *
 * <p>
 * The binding intent ({@link #binding()}) is the single source of truth shared
 * by both engines; the concrete resolution strategy (runtime chain vs AOT
 * inline code) differs per engine but reads from this same descriptor.
 * </p>
 */
public interface HandlerParam {

	/** The parameter's declared type. */
	Class<?> type();

	/**
	 * The binding name — the {@code @PathParam}/{@code @QueryParam} value, or the
	 * parameter name when no explicit value is given. Empty for parameters that do
	 * not bind to a named path/query segment.
	 */
	String bindingName();

	/**
	 * The binding intent, shared by both engines via
	 * {@code RouteInfo.ParamBinding}.
	 */
	ParamBinding binding();

	/** Whether the parameter is annotated with {@code @Valid}. */
	boolean validated();
}
