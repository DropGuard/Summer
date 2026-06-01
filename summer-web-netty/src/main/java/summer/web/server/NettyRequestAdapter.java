package summer.web.server;

import io.netty.handler.codec.http.FullHttpRequest;
import java.util.HashMap;
import java.util.Map;
import summer.web.Request;

public class NettyRequestAdapter {

	public static Request adapt(FullHttpRequest nettyReq) {
		String uri = nettyReq.uri();
		String path = uri;
		String query = "";

		int questionMarkIndex = uri.indexOf('?');
		if (questionMarkIndex != -1) {
			path = uri.substring(0, questionMarkIndex);
			query = uri.substring(questionMarkIndex + 1);
		}

		String method = nettyReq.method().name();

		Map<String, String> headers = new HashMap<>();
		for (Map.Entry<String, String> entry : nettyReq.headers()) {
			headers.put(entry.getKey().toLowerCase(), entry.getValue());
		}

		String contentType = headers.get("content-type");

		byte[] body = null;
		if (nettyReq.content().isReadable()) {
			int length = nettyReq.content().readableBytes();
			body = new byte[length];
			nettyReq.content().readBytes(body);
		}

		return new Request(method, path, query, contentType, body, headers,
				path.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}
}
