package benchmark.summer;


import summer.core.annotation.Configuration;
import summer.core.Engine;
import summer.boot.SummerApplication;

@Configuration
public class Application {
    public static void main(String[] args) throws Exception {
        SummerApplication.apply(Engine.AOT).run(args);
    }

    @summer.core.annotation.Bean
    public UserService userService() {
        return new UserService();
    }
}
