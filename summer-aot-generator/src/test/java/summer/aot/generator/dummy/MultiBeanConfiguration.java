package summer.aot.generator.dummy;
import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
@Configuration
public class MultiBeanConfiguration {
    @Bean public ServiceA serviceA() { return new ServiceA(); }
    @Bean public ServiceB serviceB() { return new ServiceB(); }
}
