package summer.example;

import io.grpc.stub.StreamObserver;
import summer.core.Component;
import summer.example.grpc.CreateUserRequest;
import summer.example.grpc.GetUserRequest;
import summer.example.grpc.UserGrpcServiceGrpc;
import summer.example.grpc.UserResponse;

@Component
public class UserGrpcServiceImpl extends UserGrpcServiceGrpc.UserGrpcServiceImplBase {

	private final UserService userService;

	public UserGrpcServiceImpl(UserService userService) {
		this.userService = userService;
	}

	@Override
	public void getUser(GetUserRequest request, StreamObserver<UserResponse> responseObserver) {
		try {
			User user = userService.findById(request.getId());
			if (user != null) {
				UserResponse response = UserResponse.newBuilder().setId(user.id()).setName(user.name())
						.setEmail(user.email()).build();
				responseObserver.onNext(response);
				responseObserver.onCompleted();
			} else {
				responseObserver.onError(io.grpc.Status.NOT_FOUND.withDescription("User not found: " + request.getId())
						.asRuntimeException());
			}
		} catch (Exception e) {
			responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void createUser(CreateUserRequest request, StreamObserver<UserResponse> responseObserver) {
		try {
			User user = new User(null, request.getName(), request.getEmail());
			User created = userService.create(user);
			UserResponse response = UserResponse.newBuilder().setId(created.id()).setName(created.name())
					.setEmail(created.email()).build();
			responseObserver.onNext(response);
			responseObserver.onCompleted();
		} catch (Exception e) {
			responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		}
	}
}
