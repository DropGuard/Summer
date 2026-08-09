package com.github.dropguard.summer.tck.invisible.fixtures.di;

import com.github.dropguard.summer.core.Component;

/** Second half of the deliberate dependency cycle (B -> A). */
@Component
public class CycleNodeB {

    private final CycleNodeA nodeA;

    public CycleNodeB(CycleNodeA nodeA) {
        this.nodeA = nodeA;
    }

    public CycleNodeA nodeA() {
        return nodeA;
    }
}
