package com.github.dropguard.summer.fixtures.dummy;

import com.github.dropguard.summer.core.Component;

/** The replaceable implementation: declared as a @Mock target in MockBehaviorTest. */
@Component
public class RealEchoService extends AbstractEchoService {

    @Override
    public String send(String message) {
        return "real:" + message;
    }
}
