package com.github.dropguard.summer.fixtures.grpc.dummy;

import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.grpc.client.GrpcChannelManager;
import com.github.dropguard.summer.grpc.test.echo.EchoServiceGrpc;

@Configuration
public class GrpcTestConfig {

	private final GrpcChannelManager channelManager;

	public GrpcTestConfig(GrpcChannelManager channelManager) {
		this.channelManager = channelManager;
	}

	@com.github.dropguard.summer.core.annotation.Bean
	public EchoServiceGrpc.EchoServiceBlockingStub echoStub() {
		return EchoServiceGrpc.newBlockingStub(channelManager.getChannel("localhost:9090"));
	}
}
