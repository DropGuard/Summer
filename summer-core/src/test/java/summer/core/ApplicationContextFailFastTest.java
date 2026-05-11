package summer.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import summer.aop.MethodInterceptor;
import summer.aop.InvocationContext;
import java.util.List;

public class ApplicationContextFailFastTest {

    @Test
    public void testAopProxyFailureCausesStartupError() {
        ApplicationContext context = new ApplicationContext();
        context.registerComponent(TestService.class);
        context.registerComponent(FailingInterceptor.class);
        
        // This should throw SummerException because FailingInterceptor will cause ProxyFactory to fail
        // (ProxyFactory requires interfaces, and TestService doesn't have any in this simple test if we don't be careful,
        // but ApplicationContext.getBean(MethodInterceptor.class) call itself might fail if things are not right.)
        
        // Actually, let's make it more direct. If createProxy throws, instantiateBean should not catch it.
        // We can simulate this by registering a component that has an interface but AOP fails for some reason.
        
        assertThrows(RuntimeException.class, () -> {
            context.initializeBeans();
        });
    }

    public interface ServiceInterface {}
    
    @Component
    public static class TestService implements ServiceInterface {}

    @Component
    public static class FailingInterceptor implements MethodInterceptor {
        @Override
        public Object intercept(InvocationContext context) throws Throwable {
            return context.proceed();
        }

        @Override
        public boolean supports(Class<?> targetClass) {
            if (targetClass == TestService.class) {
                throw new RuntimeException("Simulated AOP failure");
            }
            return false;
        }
    }
}
