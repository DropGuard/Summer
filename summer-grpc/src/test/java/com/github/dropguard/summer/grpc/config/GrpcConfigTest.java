package com.github.dropguard.summer.grpc.config;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.test.annotation.SummerTest;
import org.junit.jupiter.api.Test;

/**
 * Verifies that gRPC config binding pulls defaults correctly from
 * {@code application.yml} (none provided = all defaults).
 */
@SummerTest
class GrpcConfigTest {

    private final GrpcServerConfig serverConfig;
    private final GrpcTlsConfig tlsConfig;

    GrpcConfigTest(GrpcServerConfig serverConfig, GrpcTlsConfig tlsConfig) {
        this.serverConfig = serverConfig;
        this.tlsConfig = tlsConfig;
    }

    @Test
    void serverConfigBindsPort() {
        // Port 0 = random ephemeral port in test config; any valid port is fine.
        assertTrue(serverConfig.port() >= 0);
    }

    @Test
    void tlsConfigDefaultsToDisabled() {
        assertFalse(tlsConfig.enabled(), "TLS should default to disabled");
    }
}
