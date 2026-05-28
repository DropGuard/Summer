package benchmark.summer;


import summer.core.annotation.Configuration;
import summer.web.SummerApplication;

@Configuration
public class Application {
    public static void main(String[] args) {
        SummerApplication.builder(Application.class).useAot().run(args);
    }

    @summer.core.annotation.Bean
    public UserService userService() {
        return new UserService();
    }
}
