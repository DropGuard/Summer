package summer.boot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import summer.core.BeanContainer;
import summer.runtime.RuntimeApplicationContext;

public class SummerApplication {

    private static final Logger log = LoggerFactory.getLogger(SummerApplication.class);

    private final BeanContainer context;

    static {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }


    public SummerApplication(BeanContainer context) {
        this.context = context;
    }

    /**
     * Run with AOT bootstrap (default); falls back to runtime scanning
     * when no AOT context is present on the classpath.
     */
    public static BeanContainer run(Class<?> mainClass, String[] args) throws Exception {
        return new SummerApplication(RuntimeApplicationContext.create()).run(args);
    }

    /**
     * Run with explicit BeanContainer (e.g. a pre-built AOT context or
     * pure runtime context from {@link RuntimeApplicationContext#createRuntime()}).
     */
    public static BeanContainer run(Class<?> mainClass, String[] args, BeanContainer context)
            throws Exception {
        return new SummerApplication(context).run(args);
    }

    public BeanContainer run(String[] args) throws Exception {
        System.out.println(Banner.format());
        log.info("Starting Summer Application...");

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
