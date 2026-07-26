package com.github.dropguard.summer.fixtures.grpc.dummy;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.grpc.test.echo.EchoRequest;
import com.github.dropguard.summer.grpc.test.echo.EchoResponse;
import com.github.dropguard.summer.grpc.test.echo.EchoServiceGrpc;
import io.grpc.stub.StreamObserver;

@Component
public class EchoServiceImpl extends EchoServiceGrpc.EchoServiceImplBase {

	@Override
	public void echo(EchoRequest request, StreamObserver<EchoResponse> responseObserver) {
		String msg = request.getMessage();
		EchoResponse response = EchoResponse.newBuilder().setMessage("ECHO: " + msg).build();
		responseObserver.onNext(response);
		responseObserver.onCompleted();
	}
}
