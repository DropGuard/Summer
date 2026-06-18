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
 * // Default — auto-detect AOT, fall back to Runtime
 * SummerApplication.run(args);
 *
 * // Explicit engine
 * SummerApplication.run(Engine.AOT, args);     // fail if AOT missing
 * SummerApplication.run(Engine.RUNTIME, args); // always Runtime
 *
 * // Pre-built container
 * SummerApplication.run(container, args);
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

    /** Auto-detect: AOT first, Runtime fallback. */
    public static BeanContainer run(String[] args) throws Exception {
        return start(RuntimeApplicationContext.create(), args);
    }

    /** Explicit engine ({@link Engine#AOT} fails if no AOT context). */
    public static BeanContainer run(Engine engine, String[] args) throws Exception {
        return start(RuntimeApplicationContext.create(engine), args);
    }

    /** Pre-built {@link BeanContainer}. */
    public static BeanContainer run(BeanContainer context, String[] args) throws Exception {
        return start(context, args);
    }

    private static BeanContainer start(BeanContainer context, String[] args) throws Exception {
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
