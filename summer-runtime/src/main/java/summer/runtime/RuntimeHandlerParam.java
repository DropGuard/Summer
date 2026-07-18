package summer.runtime;

import java.lang.reflect.Parameter;
import summer.core.bean.RouteInfo.ParamBinding;
import summer.web.HandlerParam;
import summer.web.ScrollRequest;
import summer.web.annotation.PathParam;
import summer.web.annotation.QueryParam;

/**
 * Reflection-based {@link HandlerParam} adapter for the runtime engine.
 *
 * <p>
 * The runtime engine discovers handlers reflectively, so it builds the
 * reflection-free {@link HandlerParam} descriptor from a {@code Parameter} at
 * registration time. This is the single place where parameter reflection is
 * translated into the shared, engine-agnostic descriptor consumed by
 * {@link summer.web.HttpParameterResolver}.
 * </p>
 */
final class RuntimeHandlerParam implements HandlerParam {

	private final Class<?> type;
	private final String bindingName;
	private final ParamBinding binding;
	private final boolean validated;

	RuntimeHandlerParam(Parameter parameter) {
		this.type = parameter.getType();
		this.validated = parameter.isAnnotationPresent(jakarta.validation.Valid.class);
		if (parameter.isAnnotationPresent(PathParam.class)) {
			PathParam ann = parameter.getAnnotation(PathParam.class);
			this.binding = ParamBinding.PATH;
			this.bindingName = ann.value().isEmpty() ? parameter.getName() : ann.value();
		} else if (parameter.isAnnotationPresent(QueryParam.class)) {
			QueryParam ann = parameter.getAnnotation(QueryParam.class);
			this.binding = ParamBinding.QUERY;
			this.bindingName = ann.value().isEmpty() ? parameter.getName() : ann.value();
		} else if (ScrollRequest.class.isAssignableFrom(type)) {
			this.binding = ParamBinding.PAGEABLE;
			this.bindingName = "";
		} else {
			this.binding = ParamBinding.BODY;
			this.bindingName = "";
		}
	}

	@Override
	public Class<?> type() {
		return type;
	}

	@Override
	public String bindingName() {
		return bindingName;
	}

	@Override
	public ParamBinding binding() {
		return binding;
	}

	@Override
	public boolean validated() {
		return validated;
	}
}
