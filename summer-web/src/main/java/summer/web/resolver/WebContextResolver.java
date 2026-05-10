package summer.web.resolver;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Parameter;
import summer.web.ArgumentResolver;
import summer.web.WebContext;

public class WebContextResolver implements ArgumentResolver {
    @Override
    public boolean supports(Parameter parameter) {
        return parameter.getType().equals(WebContext.class);
    }

    @Override
    public MethodHandle resolve(Parameter parameter, MethodHandles.Lookup lookup) throws Exception {
        return MethodHandles.identity(WebContext.class);
    }
}
