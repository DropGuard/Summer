package com.example;

import com.github.dropguard.summer.boot.SummerApplication;
import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Engine;

/**
 * Minimal Summer app for the invoker IT. The app IS the verification: it fails (non-zero exit)
 * unless the container actually booted on the AOT engine and the AOT wire registered the bean —
 * so the nested build's exit code is the plugin's end-to-end verdict.
 */
public class App {

    public static void main(String[] args) {
        BeanContainer container = SummerApplication.run(args);

        // The plugin flipped application.yml to aot — if it did not, this is RUNTIME.
        if (container.engine() != Engine.AOT) {
            System.err.println("booted with engine " + container.engine().name() + ", expected AOT");
            System.exit(1);
        }

        // The AOT wire must have registered the component (generated context + compiled).
        if (!"hello from aot-it".equals(container.getBean(GreetingService.class).greet())) {
            System.err.println("container did not wire GreetingService through the AOT path");
            System.exit(1);
        }

        System.out.println("AOT boot verified");
    }
}
