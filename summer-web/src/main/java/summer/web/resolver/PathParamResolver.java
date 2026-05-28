package summer.web.resolver;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Parameter;
import summer.web.ArgumentResolver;
import summer.web.Request;
import summer.web.WebContext;
import summer.web.annotation.PathParam;

public class PathParamResolver implements ArgumentResolver {
	@Override
	public boolean supports(Parameter parameter) {
		return parameter.isAnnotationPresent(PathParam.class);
	}

	@Override
	public MethodHandle resolve(Parameter parameter, MethodHandles.Lookup lookup) throws Exception {
		String name = parameter.getAnnotation(PathParam.class).value();
		MethodHandle getReq = lookup.findVirtual(WebContext.class, "request", MethodType.methodType(Request.class));
		MethodHandle getParam = lookup.findVirtual(Request.class, "pathParam",
				MethodType.methodType(String.class, String.class));
		MethodHandle boundGetParam = MethodHandles.insertArguments(getParam, 1, name);
		return MethodHandles.filterArguments(boundGetParam, 0, getReq);
	}
}
