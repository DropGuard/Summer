package com.github.dropguard.summer.fixtures;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;
import io.grpc.BindableService;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerServiceDefinition;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Configuration
public class GrpcTestConfig {

    private static final Metadata.Key<String> TEST_HEADER_KEY =
            Metadata.Key.of("x-test-interceptor", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> RESPONSE_TRAILER_KEY =
            Metadata.Key.of("x-test-response", Metadata.ASCII_STRING_MARSHALLER);

    private static final MethodDescriptor.Marshaller<String> STRING_MARSHALLER =
            new MethodDescriptor.Marshaller<String>() {
                @Override
                public InputStream stream(String value) {
                    return new ByteArrayInputStream(value.getBytes());
                }

                @Override
                public String parse(InputStream stream) {
                    try {
                        return new String(stream.readAllBytes());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            };

    public static final MethodDescriptor<String, String> TEST_METHOD =
            MethodDescriptor.<String, String>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName("TestService/TestCall")
                    .setRequestMarshaller(STRING_MARSHALLER)
                    .setResponseMarshaller(STRING_MARSHALLER)
                    .build();

    /**
     * Unary method whose handler throws on {@code onHalfClose} — exercises the interceptor's
     * exception path.
     */
    public static final MethodDescriptor<String, String> UNARY_THROWS_METHOD =
            MethodDescriptor.<String, String>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName("TestService/UnaryThrows")
                    .setRequestMarshaller(STRING_MARSHALLER)
                    .setResponseMarshaller(STRING_MARSHALLER)
                    .build();

    /**
     * Client-streaming method whose handler throws on {@code onMessage} — the audit's "streaming
     * exceptions run naked" concern.
     */
    public static final MethodDescriptor<String, String> STREAMING_THROWS_METHOD =
            MethodDescriptor.<String, String>newBuilder()
                    .setType(MethodDescriptor.MethodType.CLIENT_STREAMING)
                    .setFullMethodName("TestService/StreamingThrows")
                    .setRequestMarshaller(STRING_MARSHALLER)
                    .setResponseMarshaller(STRING_MARSHALLER)
                    .build();

    @Bean
    public BindableService dummyService() {
        return new BindableService() {
            @Override
            public ServerServiceDefinition bindService() {
                return ServerServiceDefinition.builder("TestService")
                        .addMethod(
                                TEST_METHOD,
                                new ServerCallHandler<String, String>() {
                                    @Override
                                    public ServerCall.Listener<String> startCall(
                                            ServerCall<String, String> call, Metadata headers) {
                                        String headerValue = headers.get(TEST_HEADER_KEY);
                                        call.sendHeaders(new Metadata());
                                        call.sendMessage("Hello " + headerValue);

                                        Metadata trailers = new Metadata();
                                        trailers.put(RESPONSE_TRAILER_KEY, "InterceptedResponse");
                                        call.close(io.grpc.Status.OK, trailers);

                                        return new ServerCall.Listener<String>() {};
                                    }
                                })
                        .addMethod(
                                UNARY_THROWS_METHOD,
                                new ServerCallHandler<String, String>() {
                                    @Override
                                    public ServerCall.Listener<String> startCall(
                                            ServerCall<String, String> call, Metadata headers) {
                                        // gRPC flow control: must request() before listener
                                        // callbacks (onMessage/onHalfClose) are delivered.
                                        call.request(1);
                                        return new ServerCall.Listener<String>() {
                                            @Override
                                            public void onHalfClose() {
                                                throw new IllegalStateException("unary boom");
                                            }
                                        };
                                    }
                                })
                        .addMethod(
                                STREAMING_THROWS_METHOD,
                                new ServerCallHandler<String, String>() {
                                    @Override
                                    public ServerCall.Listener<String> startCall(
                                            ServerCall<String, String> call, Metadata headers) {
                                        call.request(1);
                                        return new ServerCall.Listener<String>() {
                                            @Override
                                            public void onMessage(String message) {
                                                throw new IllegalStateException("streaming boom");
                                            }
                                        };
                                    }
                                })
                        .build();
            }
        };
    }

    @Bean
    public ServerInterceptor customInterceptor() {
        return new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call,
                    Metadata headers,
                    ServerCallHandler<ReqT, RespT> next) {
                headers.put(TEST_HEADER_KEY, "Intercepted!");
                return next.startCall(call, headers);
            }
        };
    }
}
