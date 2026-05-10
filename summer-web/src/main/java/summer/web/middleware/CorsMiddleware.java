package summer.web.middleware;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import summer.web.Handler;
import summer.web.Response;

/**
 * CORS (Cross-Origin Resource Sharing) middleware for handling cross-origin
 * requests. This middleware allows or denies cross-origin requests based on
 * configuration.
 */
public class CorsMiddleware implements Middleware {

	private Set<String> allowedOrigins;
	private Set<String> allowedMethods;
	private Set<String> allowedHeaders;
	private boolean allowCredentials;
	private int maxAge;

	public CorsMiddleware() {
		// 默认配置
		allowedOrigins = new HashSet<>(Arrays.asList("*")); // 允许所有来源
		allowedMethods = new HashSet<>(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		allowedHeaders = new HashSet<>(Arrays.asList("Origin", "Content-Type", "Accept"));
		allowCredentials = true;
		maxAge = 3600; // 1 小时
	}

	public CorsMiddleware allowedOrigins(String... origins) {
		allowedOrigins = new HashSet<>(Arrays.asList(origins));
		return this;
	}

	public CorsMiddleware allowedMethods(String... methods) {
		allowedMethods = new HashSet<>(Arrays.asList(methods));
		return this;
	}

	public CorsMiddleware allowedHeaders(String... headers) {
		allowedHeaders = new HashSet<>(Arrays.asList(headers));
		return this;
	}

	public CorsMiddleware allowCredentials(boolean allow) {
		this.allowCredentials = allow;
		return this;
	}

	public CorsMiddleware maxAge(int seconds) {
		this.maxAge = seconds;
		return this;
	}

	@Override
	public Handler apply(Handler handler) {
		return (request, response) -> {
			// 处理预检请求
			if ("OPTIONS".equals(request.getMethod())) {
				setCorsHeaders(response);
				response.ok("OK");
				return null;
			}

			// 处理实际请求
			Object result = handler.handle(request, response);
			setCorsHeaders(response);
			return result;
		};
	}

	private void setCorsHeaders(Response response) {
		// 设置允许的来源
		response.setHeader("Access-Control-Allow-Origin", String.join(",", allowedOrigins));

		// 设置允许的方法
		response.setHeader("Access-Control-Allow-Methods", String.join(",", allowedMethods));

		// 设置允许的头部
		response.setHeader("Access-Control-Allow-Headers", String.join(",", allowedHeaders));

		// 设置是否允许凭证
		response.setHeader("Access-Control-Allow-Credentials", String.valueOf(allowCredentials));

		// 设置预检请求的缓存时间
		response.setHeader("Access-Control-Max-Age", String.valueOf(maxAge));
	}
}
