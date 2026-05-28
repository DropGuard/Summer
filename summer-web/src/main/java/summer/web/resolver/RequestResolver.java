package summer.web.resolver;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Parameter;
import summer.web.ArgumentResolver;
import summer.web.Request;
import summer.web.WebContext;

public class RequestResolver implements ArgumentResolver {
	@Override
	public boolean supports(Parameter parameter) {
		return parameter.getType().equals(Request.class);
	}

	@Override
	public MethodHandle resolve(Parameter parameter, MethodHandles.Lookup lookup) throws Exception {
		return lookup.findVirtual(WebContext.class, "request", MethodType.methodType(Request.class));
	}
}
