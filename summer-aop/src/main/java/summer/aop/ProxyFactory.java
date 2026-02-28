package summer.aop;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * Proxy factory that creates JDK dynamic proxies for interface-based AOP.
 */
public class ProxyFactory {
    @SuppressWarnings("unchecked")
    public static <T> T createProxy(T target, List<MethodInterceptor> interceptors) {
        // Check if target implements any interfaces
        Class<?>[] interfaces = target.getClass().getInterfaces();
        if (interfaces.length == 0) {
            throw new SummerAopException("Target object must implement at least one interface");
        }
        
        return (T) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                interfaces,
                new ProxyInvocationHandler(target, interceptors)
        );
    }

    private static class ProxyInvocationHandler implements InvocationHandler {
        private final Object target;
        private final List<MethodInterceptor> interceptors;

        ProxyInvocationHandler(Object target, List<MethodInterceptor> interceptors) {
            this.target = target;
            this.interceptors = interceptors;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // Handle Object methods specially
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(target, args);
            }
            
            InvocationContext context = new DefaultInvocationContext(
                    target, method, args
            );
            
            return createInterceptorChain(context).proceed();
        }

        private InvocationContext createInterceptorChain(InvocationContext context) {
            InvocationContext current = context;
            
            // Apply interceptors in reverse order to maintain chain
            for (int i = interceptors.size() - 1; i >= 0; i--) {
                MethodInterceptor interceptor = interceptors.get(i);
                current = new InterceptorChain(interceptor, current);
            }
            
            return current;
        }

        private static class InterceptorChain implements InvocationContext {
            private final MethodInterceptor interceptor;
            private final InvocationContext next;

            InterceptorChain(MethodInterceptor interceptor, InvocationContext next) {
                this.interceptor = interceptor;
                this.next = next;
            }

            @Override
            public Object getTarget() {
                return next.getTarget();
            }

            @Override
            public Method getMethod() {
                return next.getMethod();
            }

            @Override
            public Object[] getArguments() {
                return next.getArguments();
            }

            @Override
            public Object proceed() throws Throwable {
                return interceptor.intercept(next);
            }
        }
    }

    private static class DefaultInvocationContext implements InvocationContext {
        private final Object target;
        private final Method method;
        private final Object[] args;

        DefaultInvocationContext(Object target, Method method, Object[] args) {
            this.target = target;
            this.method = method;
            this.args = args;
        }

        @Override
        public Object getTarget() {
            return target;
        }

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public Object[] getArguments() {
            return args;
        }

        @Override
        public Object proceed() throws Throwable {
            return method.invoke(target, args);
        }
    }
}