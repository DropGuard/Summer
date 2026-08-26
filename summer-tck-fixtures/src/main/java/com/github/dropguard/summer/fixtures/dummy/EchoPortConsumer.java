package com.github.dropguard.summer.fixtures.dummy;

import com.github.dropguard.summer.core.Component;

/**
 * Consumer declaring the TRANSITIVE interface ({@code RealEchoService -> AbstractEchoService ->
 * EchoPort}) — the mock is registered under the concrete target only, so exact-key lookup can never
 * see it; only assignability resolution reaches the mock here.
 */
@Component
public class EchoPortConsumer {

    private final EchoPort port;

    public EchoPortConsumer(EchoPort port) {
        this.port = port;
    }

    public String send(String message) {
        return port.send(message);
    }
}
