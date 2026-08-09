package com.github.dropguard.summer.tck.invisible.fixtures.closeorder;

import java.util.ArrayList;
import java.util.List;

/** Shared close-order recording for the CDI-teardown contract tests. */
public final class CloseOrderRecorder {

    private static final List<String> CLOSED = new ArrayList<>();

    private CloseOrderRecorder() {}

    public static void record(String name) {
        CLOSED.add(name);
    }

    public static List<String> closed() {
        return new ArrayList<>(CLOSED);
    }

    public static void clear() {
        CLOSED.clear();
    }
}
