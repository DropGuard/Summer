// Generated stub — the gRPC protoc plugin is not available in CI, so this
// minimal hand-written stub provides only what EchoServiceImpl needs to compile.
package com.github.dropguard.summer.grpc.test.echo;

import io.grpc.stub.StreamObserver;

public final class EchoServiceGrpc {
    private EchoServiceGrpc() {}

    public abstract static class EchoServiceImplBase {
        public void echo(EchoRequest request, StreamObserver<EchoResponse> responseObserver) {
            throw new UnsupportedOperationException("gRPC stub only — not for runtime use");
        }
    }
}
