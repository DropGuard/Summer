package com.github.dropguard.summer.fixtures.aop.inherited;

/**
 * Abstract base holding the interface. Children inherit {@code EchoApi} through the CLASS hierarchy
 * only — discovery must climb superclasses for this contract to participate in AOP and DI at all.
 */
public abstract class AbstractEchoBase implements EchoApi {

    @Override
    public String echo(String in) {
        return "base:" + in;
    }
}
