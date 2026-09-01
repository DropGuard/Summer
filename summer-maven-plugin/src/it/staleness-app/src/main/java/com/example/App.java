package com.example;

import com.github.dropguard.summer.boot.SummerApplication;
import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Engine;

/**
 * Minimal Summer app for the staleness IT. The app is the verification: it fails (non-zero
 * exit) unless the container booted on the AOT engine AND every AOT-wired bean is reachable.
 *
 * <p>Between two nested builds, the IT driver deletes/renames bean source files. If the
 * reconciler fails to remove the corresponding compiled .class from the previous build, the
 * generated AOT context will reference a bean that no longer exists — and either the build
 * itself will fail to compile, or this app will report the ghost bean as missing.
 */
public class App {

    public static void main(String[] args) {
        BeanContainer container = SummerApplication.run(args);

        if (container.engine() != Engine.AOT) {
            System.err.println("booted with engine " + container.engine().name() + ", expected AOT");
            System.exit(1);
        }

        // Surviving beans must be reachable. Removed beans must NOT be reachable — that
        // would mean a stale .class survived the reconciliation.
        if (!container.containsBean(KeepService.class)) {
            System.err.println("KeepService missing from container — AOT wire broken");
            System.exit(1);
        }
        // Stale bean from a previous build would also be reachable; this must be absent.
        if (container.containsBean(RemovableService.class)) {
            System.err.println("RemovableService still wired — reconciler failed to remove stale class");
            System.exit(1);
        }

        System.out.println("staleness verified: kept beans wired, removed beans gone");
    }
}
