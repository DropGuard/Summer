package summer.boot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import summer.core.BeanContainer;
import summer.core.Engine;
import summer.runtime.RuntimeApplicationContext;

/**
 * Single entry point for Summer applications.
 *
 * <pre>{@code
 * // Production — compile-time generated wiring
 * SummerApplication.run(Engine.AOT, args);
 *
 * // Development — runtime classpath scanning
 * SummerApplication.run(Engine.RUNTIME, args);
 * }</pre>
 */
public final class SummerApplication {

    private static final Logger log = LoggerFactory.getLogger(SummerApplication.class);

    static {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }

    private SummerApplication() {
    }

    public static BeanContainer run(Engine engine, String[] args) throws Exception {
        BeanContainer context = RuntimeApplicationContext.create(engine);
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
