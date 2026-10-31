package com.github.dropguard.summer.tck.invisible.fixtures.lifecycle;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.FrozenState;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Self-timed: freezes on the last line of its own constructor, no container involvement. */
@Component
public class SelfFreezingBean {

    public static final List<String> WRITTEN = new CopyOnWriteArrayList<>();

    private final FrozenState state = new FrozenState();

    public SelfFreezingBean() {
        state.freeze();
    }

    public void append(String value) {
        state.ensureMutable("append");
        WRITTEN.add(value);
    }

    public void seal() {
        state.freeze();
    }
}
