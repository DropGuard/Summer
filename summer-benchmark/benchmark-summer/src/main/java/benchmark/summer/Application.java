package benchmark.summer;


import summer.core.annotation.Configuration;
import summer.boot.SummerApplication;

@Configuration
public class Application {
    public static void main(String[] args) throws Exception {
        SummerApplication.run(Application.class, args);
    }

    @summer.core.annotation.Bean
    public UserService userService() {
        return new UserService();
    }
}
