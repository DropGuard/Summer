package com.github.dropguard.summer.fixtures.dummy;

/**
 * Abstract base holding the {@link EchoPort} contract — consumers may declare THIS type (or the
 * interface) while the concrete bean (and its @Mock replacement) is a subclass.
 */
public abstract class AbstractEchoService implements EchoPort {

    @Override
    public abstract String send(String message);
}
