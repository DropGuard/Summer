package summer.web.server;

import java.util.List;
import summer.web.BodyConverter;
import summer.web.ExceptionRegistry;
import summer.web.HttpRouter;
import summer.web.Middleware;
import summer.web.WsRouter;
import summer.web.websocket.WsInterceptor;

public record WebServerDependencies(HttpRouter httpRouter, WsRouter wsRouter, List<Middleware> middlewares,
		BodyConverter jsonConverter, ExceptionRegistry exceptionRegistry, List<WsInterceptor> wsInterceptors,
		WebSocketUpgradeHandler wsUpgradeHandler) {
}
