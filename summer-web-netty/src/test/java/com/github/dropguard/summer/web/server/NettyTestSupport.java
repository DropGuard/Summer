package com.github.dropguard.summer.web.server;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared EmbeddedChannel factory for Netty unit tests. Encapsulates three traps this suite already
 * paid for:
 *
 * <ol>
 *   <li>{@code EmbeddedChannelId} is a process-wide singleton in current Netty, while {@code
 *       DefaultChannelGroup} deduplicates channels by id — every channel must carry an explicit
 *       distinct id or multi-client scenarios silently collapse into one entry.
 *   <li>A freshly created channel has an empty pipeline, so {@code firstContext()} returns null
 *       exactly when a test needs a ChannelHandlerContext — the no-arg flavour installs a tail
 *       handler to guarantee one.
 *   <li>Scheduled-task tests must pair {@code advanceTimeBy} (the embedded scheduling clock) with
 *       whatever clock the handler injects for elapsed-time decisions — they are two clocks, and
 *       advancing one does not advance the other.
 * </ol>
 */
final class NettyTestSupport {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private NettyTestSupport() {}

    /**
     * Creates an EmbeddedChannel with a unique explicit id and, unless handlers are supplied, a
     * tail handler so the pipeline always yields a usable first context.
     */
    static EmbeddedChannel newChannel(ChannelHandler... handlers) {
        int n = SEQ.incrementAndGet();
        ChannelId id =
                new ChannelId() {
                    @Override
                    public String asShortText() {
                        return "embedded-" + n;
                    }

                    @Override
                    public String asLongText() {
                        return asShortText();
                    }

                    @Override
                    public int compareTo(ChannelId o) {
                        return asLongText().compareTo(o.asLongText());
                    }
                };
        if (handlers.length == 0) {
            return new EmbeddedChannel(id, new ChannelInboundHandlerAdapter());
        }
        return new EmbeddedChannel(id, handlers);
    }

    static ChannelHandlerContext firstContext(EmbeddedChannel channel) {
        return channel.pipeline().firstContext();
    }
}
