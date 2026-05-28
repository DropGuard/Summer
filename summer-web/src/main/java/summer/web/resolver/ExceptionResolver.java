package summer.web.resolver;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Parameter;
import summer.web.ArgumentResolver;
import summer.web.Request;
import summer.web.WebContext;

public class ExceptionResolver implements ArgumentResolver {
	@Override
	public boolean supports(Parameter parameter) {
		return Throwable.class.isAssignableFrom(parameter.getType());
	}

	@Override
	public MethodHandle resolve(Parameter parameter, MethodHandles.Lookup lookup) throws Exception {
		Class<?> type = parameter.getType();
		MethodHandle getReq = lookup.findVirtual(WebContext.class, "request", MethodType.methodType(Request.class));
		MethodHandle getAttr = lookup.findVirtual(Request.class, "getAttribute",
				MethodType.methodType(Object.class, String.class));
		MethodHandle boundGetAttr = MethodHandles.insertArguments(getAttr, 1, "last_exception");
		// Cast the object to the specific Throwable type
		return MethodHandles.filterArguments(boundGetAttr.asType(MethodType.methodType(type, Request.class)), 0,
				getReq);
	}
}
