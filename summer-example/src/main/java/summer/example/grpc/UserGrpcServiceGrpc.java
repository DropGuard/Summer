package summer.example.grpc;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.73.0)",
    comments = "Source: user.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class UserGrpcServiceGrpc {

  private UserGrpcServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "summer.example.grpc.UserGrpcService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<summer.example.grpc.GetUserRequest,
      summer.example.grpc.UserResponse> getGetUserMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetUser",
      requestType = summer.example.grpc.GetUserRequest.class,
      responseType = summer.example.grpc.UserResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<summer.example.grpc.GetUserRequest,
      summer.example.grpc.UserResponse> getGetUserMethod() {
    io.grpc.MethodDescriptor<summer.example.grpc.GetUserRequest, summer.example.grpc.UserResponse> getGetUserMethod;
    if ((getGetUserMethod = UserGrpcServiceGrpc.getGetUserMethod) == null) {
      synchronized (UserGrpcServiceGrpc.class) {
        if ((getGetUserMethod = UserGrpcServiceGrpc.getGetUserMethod) == null) {
          UserGrpcServiceGrpc.getGetUserMethod = getGetUserMethod =
              io.grpc.MethodDescriptor.<summer.example.grpc.GetUserRequest, summer.example.grpc.UserResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetUser"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  summer.example.grpc.GetUserRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  summer.example.grpc.UserResponse.getDefaultInstance()))
              .setSchemaDescriptor(new UserGrpcServiceMethodDescriptorSupplier("GetUser"))
              .build();
        }
      }
    }
    return getGetUserMethod;
  }

  private static volatile io.grpc.MethodDescriptor<summer.example.grpc.CreateUserRequest,
      summer.example.grpc.UserResponse> getCreateUserMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "CreateUser",
      requestType = summer.example.grpc.CreateUserRequest.class,
      responseType = summer.example.grpc.UserResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<summer.example.grpc.CreateUserRequest,
      summer.example.grpc.UserResponse> getCreateUserMethod() {
    io.grpc.MethodDescriptor<summer.example.grpc.CreateUserRequest, summer.example.grpc.UserResponse> getCreateUserMethod;
    if ((getCreateUserMethod = UserGrpcServiceGrpc.getCreateUserMethod) == null) {
      synchronized (UserGrpcServiceGrpc.class) {
        if ((getCreateUserMethod = UserGrpcServiceGrpc.getCreateUserMethod) == null) {
          UserGrpcServiceGrpc.getCreateUserMethod = getCreateUserMethod =
              io.grpc.MethodDescriptor.<summer.example.grpc.CreateUserRequest, summer.example.grpc.UserResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CreateUser"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  summer.example.grpc.CreateUserRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  summer.example.grpc.UserResponse.getDefaultInstance()))
              .setSchemaDescriptor(new UserGrpcServiceMethodDescriptorSupplier("CreateUser"))
              .build();
        }
      }
    }
    return getCreateUserMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static UserGrpcServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<UserGrpcServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<UserGrpcServiceStub>() {
        @java.lang.Override
        public UserGrpcServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new UserGrpcServiceStub(channel, callOptions);
        }
      };
    return UserGrpcServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static UserGrpcServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<UserGrpcServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<UserGrpcServiceBlockingV2Stub>() {
        @java.lang.Override
        public UserGrpcServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new UserGrpcServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return UserGrpcServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static UserGrpcServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<UserGrpcServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<UserGrpcServiceBlockingStub>() {
        @java.lang.Override
        public UserGrpcServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new UserGrpcServiceBlockingStub(channel, callOptions);
        }
      };
    return UserGrpcServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static UserGrpcServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<UserGrpcServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<UserGrpcServiceFutureStub>() {
        @java.lang.Override
        public UserGrpcServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new UserGrpcServiceFutureStub(channel, callOptions);
        }
      };
    return UserGrpcServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void getUser(summer.example.grpc.GetUserRequest request,
        io.grpc.stub.StreamObserver<summer.example.grpc.UserResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetUserMethod(), responseObserver);
    }

    /**
     */
    default void createUser(summer.example.grpc.CreateUserRequest request,
        io.grpc.stub.StreamObserver<summer.example.grpc.UserResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCreateUserMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service UserGrpcService.
   */
  public static abstract class UserGrpcServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return UserGrpcServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service UserGrpcService.
   */
  public static final class UserGrpcServiceStub
      extends io.grpc.stub.AbstractAsyncStub<UserGrpcServiceStub> {
    private UserGrpcServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected UserGrpcServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new UserGrpcServiceStub(channel, callOptions);
    }

    /**
     */
    public void getUser(summer.example.grpc.GetUserRequest request,
        io.grpc.stub.StreamObserver<summer.example.grpc.UserResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetUserMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void createUser(summer.example.grpc.CreateUserRequest request,
        io.grpc.stub.StreamObserver<summer.example.grpc.UserResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCreateUserMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service UserGrpcService.
   */
  public static final class UserGrpcServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<UserGrpcServiceBlockingV2Stub> {
    private UserGrpcServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected UserGrpcServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new UserGrpcServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     */
    public summer.example.grpc.UserResponse getUser(summer.example.grpc.GetUserRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetUserMethod(), getCallOptions(), request);
    }

    /**
     */
    public summer.example.grpc.UserResponse createUser(summer.example.grpc.CreateUserRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCreateUserMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service UserGrpcService.
   */
  public static final class UserGrpcServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<UserGrpcServiceBlockingStub> {
    private UserGrpcServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected UserGrpcServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new UserGrpcServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public summer.example.grpc.UserResponse getUser(summer.example.grpc.GetUserRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetUserMethod(), getCallOptions(), request);
    }

    /**
     */
    public summer.example.grpc.UserResponse createUser(summer.example.grpc.CreateUserRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCreateUserMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service UserGrpcService.
   */
  public static final class UserGrpcServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<UserGrpcServiceFutureStub> {
    private UserGrpcServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected UserGrpcServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new UserGrpcServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<summer.example.grpc.UserResponse> getUser(
        summer.example.grpc.GetUserRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetUserMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<summer.example.grpc.UserResponse> createUser(
        summer.example.grpc.CreateUserRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCreateUserMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_GET_USER = 0;
  private static final int METHODID_CREATE_USER = 1;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_GET_USER:
          serviceImpl.getUser((summer.example.grpc.GetUserRequest) request,
              (io.grpc.stub.StreamObserver<summer.example.grpc.UserResponse>) responseObserver);
          break;
        case METHODID_CREATE_USER:
          serviceImpl.createUser((summer.example.grpc.CreateUserRequest) request,
              (io.grpc.stub.StreamObserver<summer.example.grpc.UserResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getGetUserMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              summer.example.grpc.GetUserRequest,
              summer.example.grpc.UserResponse>(
                service, METHODID_GET_USER)))
        .addMethod(
          getCreateUserMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              summer.example.grpc.CreateUserRequest,
              summer.example.grpc.UserResponse>(
                service, METHODID_CREATE_USER)))
        .build();
  }

  private static abstract class UserGrpcServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    UserGrpcServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return summer.example.grpc.User.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("UserGrpcService");
    }
  }

  private static final class UserGrpcServiceFileDescriptorSupplier
      extends UserGrpcServiceBaseDescriptorSupplier {
    UserGrpcServiceFileDescriptorSupplier() {}
  }

  private static final class UserGrpcServiceMethodDescriptorSupplier
      extends UserGrpcServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    UserGrpcServiceMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (UserGrpcServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new UserGrpcServiceFileDescriptorSupplier())
              .addMethod(getGetUserMethod())
              .addMethod(getCreateUserMethod())
              .build();
        }
      }
    }
    return result;
  }
}
