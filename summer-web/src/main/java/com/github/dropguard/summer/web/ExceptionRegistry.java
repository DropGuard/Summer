mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.web;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import java.util.HashMap;
mport com.github.dropguard.summer.core.Internal;
import java.util.Map;
mport com.github.dropguard.summer.core.Internal;
@Internal

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * Registry for global exception handlers. Built during startup by {@link ExceptionHandlerRegistrar}
mport com.github.dropguard.summer.core.Internal;
 * and used by the server to handle exceptions.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public class ExceptionRegistry {
mport com.github.dropguard.summer.core.Internal;
    private final Map<Class<? extends Throwable>, Handler> handlers = new HashMap<>();
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public void register(Class<? extends Throwable> exceptionType, Handler handler) {
mport com.github.dropguard.summer.core.Internal;
        handlers.put(exceptionType, handler);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public Handler getHandler(Throwable throwable) {
mport com.github.dropguard.summer.core.Internal;
        Class<?> current = throwable.getClass();
mport com.github.dropguard.summer.core.Internal;
        while (current != null && Throwable.class.isAssignableFrom(current)) {
mport com.github.dropguard.summer.core.Internal;
            Handler handler = handlers.get(current);
mport com.github.dropguard.summer.core.Internal;
            if (handler != null) {
mport com.github.dropguard.summer.core.Internal;
                return handler;
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            current = current.getSuperclass();
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return null;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
