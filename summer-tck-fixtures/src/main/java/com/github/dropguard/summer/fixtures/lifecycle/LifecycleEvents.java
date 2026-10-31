package com.github.dropguard.summer.fixtures.lifecycle;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Shared shutdown-event recorder for the lifecycle fixtures. Tests assert on the exact order. */
public final class LifecycleEvents {

    public static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    private LifecycleEvents() {}
}
