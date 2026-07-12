package summer.web;

import java.util.List;
import summer.core.config.ConfigurationProperties;
import summer.core.config.DefaultValue;

/**
 * Immutable server configuration bound from {@code application.yml}.
 */
@ConfigurationProperties(prefix = "server")
public record ServerConfig(@DefaultValue("8080") Integer port, @DefaultValue("30000") Integer connectionTimeout,
		@DefaultValue("10485760") Integer maxBodySize, @DefaultValue("10000") Integer readTimeout,
		List<String> allowedOrigins, @DefaultValue("65536") Integer maxWebSocketFrameSize,
		@DefaultValue("RADIX_TREE") RouterType routerType) {

	public boolean isOriginAllowed(String origin, String requestHost) {
		if (allowedOrigins.isEmpty()) {
			return isSameOrigin(origin, requestHost);
		}
		if (allowedOrigins.contains("*")) {
			return true;
		}
		return origin != null && allowedOrigins.contains(origin);
	}

	private boolean isSameOrigin(String origin, String requestHost) {
		if (origin == null || requestHost == null) {
			return false;
		}
		try {
			java.net.URI uri = java.net.URI.create(origin);
			String originHost = uri.getHost();
			if (originHost == null) {
				return false;
			}
			int originPort = uri.getPort() != -1 ? uri.getPort() : "https".equals(uri.getScheme()) ? 443 : 80;

			String reqHost;
			int reqPort;
			int colonIndex = requestHost.lastIndexOf(':');
			if (colonIndex != -1) {
				reqHost = requestHost.substring(0, colonIndex);
				reqPort = Integer.parseInt(requestHost.substring(colonIndex + 1));
			} else {
				reqHost = requestHost;
				reqPort = 80;
			}

			return reqHost.equals(originHost) && reqPort == originPort;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}
}
