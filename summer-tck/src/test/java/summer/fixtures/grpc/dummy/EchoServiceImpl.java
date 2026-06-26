package summer.fixtures.grpc.dummy;

import io.grpc.stub.StreamObserver;
import summer.core.Component;
import summer.grpc.test.echo.EchoRequest;
import summer.grpc.test.echo.EchoResponse;
import summer.grpc.test.echo.EchoServiceGrpc;

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
