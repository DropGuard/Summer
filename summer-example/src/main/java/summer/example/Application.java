package summer.example;

import summer.core.ApplicationContext;
import summer.web.HttpServer;
import summer.web.Router;
import summer.web.AnnotationRouterAdapter;
import summer.web.LoggingMiddleware;
import summer.web.ExceptionMiddleware;
import summer.validation.DefaultBodyValidator;
import summer.web.Router;
import summer.web.AnnotationRouterAdapter;

import java.util.List;
public class Application {
    public static void main(String[] args) {
        try {
            // 1. Initialize application context
            ApplicationContext context = new ApplicationContext();
            
            // 2. Register all required components manually
            context.registerComponent(Router.class);
            context.registerComponent(AnnotationRouterAdapter.class);
            context.registerComponent(DefaultBodyValidator.class);
            
            // Register example components
            context.registerComponent(UserController.class);
            context.registerComponent(UserServiceImpl.class);
            context.registerComponent(UserRepository.class);
            
            // Initialize beans - we need to call this directly
            context.initializeBeans();
            
            // Debug: Print all registered component classes
            System.out.println("Registered component classes:");
            for (Class<?> clazz : context.getComponentClasses()) {
                System.out.println("- " + clazz.getName());
            }
            
            // 3. Initialize router and register routes
            Router router = context.getBean(Router.class);
            AnnotationRouterAdapter routerAdapter = context.getBean(AnnotationRouterAdapter.class);
            routerAdapter.registerControllers();
            
            // 3. Start HTTP server
            int port = 8080;
            HttpServer server = HttpServer.create(port, router, List.of(
                    new LoggingMiddleware(),
                    new ExceptionMiddleware()
            ));
            server.start();
            
            // 4. Keep the application running
            System.out.println("Summer application started on http://localhost:" + port);
            System.out.println("Press Ctrl+C to stop");
            
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
            
        } catch (Exception e) {
            System.err.println("Failed to start application: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}