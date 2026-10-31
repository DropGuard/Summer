package com.github.dropguard.summer.tck.invisible.fixtures.lifecycle;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.FrozenState;
import com.github.dropguard.summer.core.Sealable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Container-timed + guarded: the pairing the {@code JdbcTemplate} uses. */
@Component
public class ContainerTimedSealable implements Sealable {

    public static final AtomicBoolean SEALED = new AtomicBoolean(false);
    public static final AtomicInteger SEAL_COUNT = new AtomicInteger();

    private final FrozenState state = new FrozenState();

    @Override
    public void seal() {
        SEAL_COUNT.incrementAndGet();
        SEALED.set(true);
        state.freeze();
    }

    public void mutate() {
        state.ensureMutable("mutate");
    }
}
