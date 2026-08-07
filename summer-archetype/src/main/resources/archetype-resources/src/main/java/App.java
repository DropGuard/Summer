package ${package};

import com.github.dropguard.summer.boot.SummerApplication;

/**
 * Application entry point. The engine is read from application.yml — the Maven plugin flips it
 * to AOT at build time, so production builds run on the AOT engine.
 */
public class App {

    public static void main(String[] args) {
        SummerApplication.run(args);
    }
}
