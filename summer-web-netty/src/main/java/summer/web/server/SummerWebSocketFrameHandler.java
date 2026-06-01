package summer.web.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

public class SummerWebSocketFrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

	private final NettyWebSocketContext wsContext;

	public SummerWebSocketFrameHandler(NettyWebSocketContext wsContext) {
		this.wsContext = wsContext;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
		wsContext.invokeMessageConsumer(frame.text());
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		wsContext.invokeCloseHandler();
		super.channelInactive(ctx);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace();
		ctx.close();
	}
}
