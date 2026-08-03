package com.github.dropguard.summer.web;

import java.util.List;

/**
 * Origin-check guard for WebSocket upgrades, factored out of {@link ServerConfig} (which is now a
 * config mapping and may not carry behaviour). Reads the allowed origins from {@link ServerConfig}
 * and decides whether a given request origin may open a WebSocket against the request host.
 *
 * <p>Delegates the actual matching to {@link OriginPolicy} so WebSocket and HTTP CORS share one
 * consistent origin rule.
 *
 * <p>Registered as a {@code @Bean} by {@link WebInfrastructureConfiguration} rather than carrying
 * {@code @Component} directly — framework code in the {@code web} package must not be annotated
 * with {@code @Component} (see {@code
 * ArchitectureTest.frameworkCodeMustUseConfigurationNotComponent}).
 */
public class ServerOriginChecker {

    private final ServerConfig config;

    public ServerOriginChecker(ServerConfig config) {
        this.config = config;
    }

    public boolean isOriginAllowed(String origin, String requestHost) {
        List<String> allowedOrigins = config.allowedOrigins();
        return OriginPolicy.isAllowed(origin, allowedOrigins, requestHost);
    }
}
