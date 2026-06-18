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
 * SummerApplication.run(Engine.AOT, args);
 * SummerApplication.run(Engine.RUNTIME, args);
 *
 * // Pre-built container (tests, custom bootstraps)
 * SummerApplication.run(container, args);
 * }</pre>
 */
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
     * Run with auto-detection: AOT if the generated context is on the
     * classpath, otherwise fall back to runtime Jandex scanning.
     */
    public static BeanContainer run(String[] args) throws Exception {
        return new SummerApplication(RuntimeApplicationContext.create()).start(args);
    }

    /**
     * Run with an explicit engine.
     *
     * @param engine {@link Engine#AOT} (fail if no AOT context) or
     *               {@link Engine#RUNTIME} (always classpath scanning)
     */
    public static BeanContainer run(Engine engine, String[] args) throws Exception {
        BeanContainer ctx = engine == Engine.AOT
                ? RuntimeApplicationContext.createAot()
                : RuntimeApplicationContext.createRuntime();
        return new SummerApplication(ctx).start(args);
    }

    /**
     * Run with a pre-built {@link BeanContainer}.
     */
    public static BeanContainer run(BeanContainer context, String[] args) throws Exception {
        return new SummerApplication(context).start(args);
    }

    public BeanContainer start(String[] args) throws Exception {
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
