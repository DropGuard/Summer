mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.web;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import java.util.List;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * Origin-check guard for WebSocket upgrades, factored out of {@link ServerConfig} (which is now a
mport com.github.dropguard.summer.core.Internal;
 * config mapping and may not carry behaviour). Reads the allowed origins from {@link ServerConfig}
mport com.github.dropguard.summer.core.Internal;
 * and decides whether a given request origin may open a WebSocket against the request host.
mport com.github.dropguard.summer.core.Internal;
@Internal
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>Delegates the actual matching to {@link OriginPolicy} so WebSocket and HTTP CORS share one
mport com.github.dropguard.summer.core.Internal;
 * consistent origin rule.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>Registered as a {@code @Bean} by {@link WebInfrastructureConfiguration} rather than carrying
mport com.github.dropguard.summer.core.Internal;
 * {@code @Component} directly — framework code in the {@code web} package must not be annotated
mport com.github.dropguard.summer.core.Internal;
 * with {@code @Component} (see {@code
mport com.github.dropguard.summer.core.Internal;
 * ArchitectureTest.frameworkCodeMustUseConfigurationNotComponent}).
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public class ServerOriginChecker {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private final ServerConfig config;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public ServerOriginChecker(ServerConfig config) {
mport com.github.dropguard.summer.core.Internal;
        this.config = config;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public boolean isOriginAllowed(String origin, String requestHost) {
mport com.github.dropguard.summer.core.Internal;
        List<String> allowedOrigins = config.allowedOrigins();
mport com.github.dropguard.summer.core.Internal;
        return OriginPolicy.isAllowed(origin, allowedOrigins, requestHost);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
