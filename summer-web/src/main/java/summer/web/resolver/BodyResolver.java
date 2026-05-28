package summer.web.resolver;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Parameter;
import summer.web.ArgumentResolver;
import summer.web.WebContext;

/**
 * Fallback resolver that treats the parameter as the request body.
 */
public class BodyResolver implements ArgumentResolver {
	@Override
	public boolean supports(Parameter parameter) {
		// Fallback: if no other resolver matches, assume it's the body
		return true;
	}

	@Override
	public MethodHandle resolve(Parameter parameter, MethodHandles.Lookup lookup) throws Exception {
		Class<?> type = parameter.getType();
		MethodHandle bodyHandler = lookup.findVirtual(WebContext.class, "body",
				MethodType.methodType(Object.class, Class.class));
		return MethodHandles.insertArguments(bodyHandler, 1, type)
				.asType(MethodType.methodType(type, WebContext.class));
	}
}
