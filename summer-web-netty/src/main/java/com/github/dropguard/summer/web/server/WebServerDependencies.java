package com.github.dropguard.summer.web.server;

import com.github.dropguard.summer.web.BodyConverter;
import com.github.dropguard.summer.web.ExceptionRegistry;
import com.github.dropguard.summer.web.HttpRouter;
import com.github.dropguard.summer.web.Middleware;
import com.github.dropguard.summer.web.WsRouter;
import com.github.dropguard.summer.web.websocket.WsInterceptor;
import java.util.List;

record WebServerDependencies(
        HttpRouter httpRouter,
        WsRouter wsRouter,
        List<Middleware> middlewares,
        BodyConverter jsonConverter,
        ExceptionRegistry exceptionRegistry,
        List<WsInterceptor> wsInterceptors,
        WebSocketUpgradeHandler wsUpgradeHandler) {}
