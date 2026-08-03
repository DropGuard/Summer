package com.github.dropguard.summer.fixtures.grpc.dummy;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.grpc.test.echo.EchoServiceGrpc;
import io.grpc.BindableService;
import io.grpc.ServerServiceDefinition;

@Configuration
public class GrpcTestConfig {

    @Bean
    public BindableService echoService() {
        return new EchoServiceImpl();
    }
}
