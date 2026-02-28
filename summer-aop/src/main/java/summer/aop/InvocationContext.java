package summer.aop;

import java.lang.reflect.Method;

/**
 * Context information about the intercepted method call.
 */
public interface InvocationContext {
    Object getTarget();
    Method getMethod();
    Object[] getArguments();
    Object proceed() throws Throwable;
}