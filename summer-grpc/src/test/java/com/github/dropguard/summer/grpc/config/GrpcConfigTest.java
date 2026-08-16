package com.github.dropguard.summer.grpc.config;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;

/**
 * Verifies that gRPC config binding pulls defaults correctly from {@code application.yml} (none
 * provided = all defaults).
 */
@SummerTest
class GrpcConfigTest {

    private final GrpcServerConfig serverConfig;
    private final GrpcServerTlsConfig serverTlsConfig;
    private final GrpcClientTlsConfig clientTlsConfig;

    GrpcConfigTest(
            GrpcServerConfig serverConfig,
            GrpcServerTlsConfig serverTlsConfig,
            GrpcClientTlsConfig clientTlsConfig) {
        this.serverConfig = serverConfig;
        this.serverTlsConfig = serverTlsConfig;
        this.clientTlsConfig = clientTlsConfig;
    }

    @DualEngine
    void serverConfigBindsPort() {
        // Port 0 = random ephemeral port in test config; any valid port is fine.
        assertTrue(serverConfig.port() >= 0);
    }

    @DualEngine
    void serverTlsConfigDefaultsToDisabled() {
        assertFalse(serverTlsConfig.enabled(), "server TLS should default to disabled");
    }

    @DualEngine
    void clientTlsConfigDefaultsToDisabled() {
        assertFalse(clientTlsConfig.enabled(), "client TLS should default to disabled");
    }
}
