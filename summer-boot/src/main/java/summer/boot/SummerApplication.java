package summer.boot;

import java.util.logging.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import summer.core.BeanContainer;
import summer.core.BeanContainer;
import summer.runtime.RuntimeApplicationContext;

public class SummerApplication {

    private static final Logger log = LoggerFactory.getLogger(SummerApplication.class);

    private final BeanContainer context;

    public SummerApplication(BeanContainer context) {
        this.context = context;
    }

    /**
     * Convenience constructor using runtime scanning.
     */
    public SummerApplication(Class<?> mainClass) {
        this(buildRuntimeContext());
    }

    private static BeanContainer buildRuntimeContext() {
        return RuntimeApplicationContext.create();
    }

    static {
        LogManager.getLogManager().reset();
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }

    /**
     * Detects the AOT bootstrap, falling back to runtime scanning.
     */
    public static BeanContainer detectContext() {
        try {
            Class<?> aotClass = Class.forName("summer.core.aot.GeneratedAotContext");
            java.lang.reflect.Method createMethod = aotClass.getMethod("create");
            return (BeanContainer) createMethod.invoke(null);
        } catch (Exception e) {
            log.debug("No AOT context found, falling back to runtime: {}", e.getMessage());
            return RuntimeApplicationContext.create();
        }
    }

    /**
     * Run with explicit BeanContainer (e.g. AOT generated).
     */
    public static BeanContainer run(Class<?> mainClass, String[] args, BeanContainer context)
            throws Exception {
        return new SummerApplication(context).run(args);
    }

    /**
     * Run with AOT detection (preferred) or runtime scanning.
     */
    public static BeanContainer run(Class<?> mainClass, String[] args) throws Exception {
        return new SummerApplication(detectContext()).run(args);
    }

    public BeanContainer run(String[] args) throws Exception {
        System.out.println(Banner.format()); // Intentional: banner goes to stdout
        log.info("Starting Summer Application...");

        // ==========================================
        // Execute Application Runners
        // ==========================================
        for (var runner : context.getBeans(summer.core.ApplicationRunner.class)) {
            runner.run(context);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down BeanContainer...");
            try {
                context.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));

        log.info("Summer application started.");
        return context;
    }
}