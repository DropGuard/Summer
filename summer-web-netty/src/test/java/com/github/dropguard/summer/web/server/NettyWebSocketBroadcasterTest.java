package com.github.dropguard.summer.web.server;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.web.BodyConverter;
import com.github.dropguard.summer.web.websocket.WebSocketContext;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pins the broadcaster's delivery-scope contract: connection registration happens at session
 * establishment (transport-driven), rooms are explicit opt-in, and leaving a room must never remove
 * a client from the global delivery scope (the S-05 regression).
 */
class NettyWebSocketBroadcasterTest {

    private final NettyWebSocketBroadcaster broadcaster = new NettyWebSocketBroadcaster();

    private record CtxPair(EmbeddedChannel channel, NettyWebSocketContext context) {}

    private CtxPair newClient() {
        EmbeddedChannel channel = NettyTestSupport.newChannel();
        ChannelHandlerContext ctx = NettyTestSupport.firstContext(channel);
        NettyWebSocketContext context =
                new NettyWebSocketContext(
                        ctx,
                        Map.of(),
                        Map.of(),
                        List.of(),
                        org.mockito.Mockito.mock(BodyConverter.class));
        broadcaster.connected(context);
        return new CtxPair(channel, context);
    }

    private static String poll(EmbeddedChannel channel) {
        TextWebSocketFrame frame = channel.readOutbound();
        return frame != null ? frame.text() : null;
    }

    @Test
    void leavingOneRoomKeepsGlobalDeliveryScope() {
        CtxPair a = newClient();
        CtxPair b = newClient();
        broadcaster.join("room", a.context());
        broadcaster.join("room", b.context());

        broadcaster.leave("room", a.context());
        broadcaster.broadcast("room", "room-msg");

        assertEquals("room-msg", poll(b.channel()), "b stayed in the room");
        assertNull(poll(a.channel()), "a left the room");

        // THE S-05 REGRESSION PIN: 'a' left its only room but is still a live,
        // connected session — broadcastAll must reach it.
        broadcaster.broadcastAll("all-msg");
        assertEquals(
                "all-msg",
                poll(a.channel()),
                "leaving a room must not evict a connected client from "
                        + "the global delivery scope");
        assertEquals("all-msg", poll(b.channel()));
    }

    @Test
    void broadcastAllReachesClientsThatNeverJoinedAnyRoom() {
        CtxPair loner = newClient(); // connected, never joined anything

        broadcaster.broadcastAll("hello-everyone");
        assertEquals(
                "hello-everyone",
                poll(loner.channel()),
                "broadcastAll means ALL live connections, not just room members");
    }

    @Test
    void emptyRoomIsEvictedImmediately() {
        CtxPair c = newClient();
        broadcaster.join("ephemeral", c.context());
        assertEquals(1, broadcaster.trackedRoomCount());

        broadcaster.leave("ephemeral", c.context());
        assertEquals(0, broadcaster.trackedRoomCount(), "empty rooms must not accumulate");
    }

    @Test
    void foreignContextImplementationsAreRejectedLoudly() {
        WebSocketContext foreign = org.mockito.Mockito.mock(WebSocketContext.class);

        assertThrows(
                IllegalArgumentException.class,
                () -> broadcaster.join("room", foreign),
                "silently ignoring an unrecognised context hides programmer error");
        assertThrows(IllegalArgumentException.class, () -> broadcaster.leave("room", foreign));
    }
}
