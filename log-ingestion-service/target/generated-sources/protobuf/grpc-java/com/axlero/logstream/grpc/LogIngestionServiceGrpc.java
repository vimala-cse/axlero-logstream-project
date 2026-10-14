package com.axlero.logstream.grpc;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.62.2)",
    comments = "Source: log_service.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class LogIngestionServiceGrpc {

  private LogIngestionServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "logstream.LogIngestionService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.axlero.logstream.grpc.LogEntry,
      com.axlero.logstream.grpc.LogAck> getSendLogMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SendLog",
      requestType = com.axlero.logstream.grpc.LogEntry.class,
      responseType = com.axlero.logstream.grpc.LogAck.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.axlero.logstream.grpc.LogEntry,
      com.axlero.logstream.grpc.LogAck> getSendLogMethod() {
    io.grpc.MethodDescriptor<com.axlero.logstream.grpc.LogEntry, com.axlero.logstream.grpc.LogAck> getSendLogMethod;
    if ((getSendLogMethod = LogIngestionServiceGrpc.getSendLogMethod) == null) {
      synchronized (LogIngestionServiceGrpc.class) {
        if ((getSendLogMethod = LogIngestionServiceGrpc.getSendLogMethod) == null) {
          LogIngestionServiceGrpc.getSendLogMethod = getSendLogMethod =
              io.grpc.MethodDescriptor.<com.axlero.logstream.grpc.LogEntry, com.axlero.logstream.grpc.LogAck>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SendLog"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.axlero.logstream.grpc.LogEntry.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.axlero.logstream.grpc.LogAck.getDefaultInstance()))
              .setSchemaDescriptor(new LogIngestionServiceMethodDescriptorSupplier("SendLog"))
              .build();
        }
      }
    }
    return getSendLogMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static LogIngestionServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<LogIngestionServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<LogIngestionServiceStub>() {
        @java.lang.Override
        public LogIngestionServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new LogIngestionServiceStub(channel, callOptions);
        }
      };
    return LogIngestionServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static LogIngestionServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<LogIngestionServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<LogIngestionServiceBlockingStub>() {
        @java.lang.Override
        public LogIngestionServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new LogIngestionServiceBlockingStub(channel, callOptions);
        }
      };
    return LogIngestionServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static LogIngestionServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<LogIngestionServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<LogIngestionServiceFutureStub>() {
        @java.lang.Override
        public LogIngestionServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new LogIngestionServiceFutureStub(channel, callOptions);
        }
      };
    return LogIngestionServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void sendLog(com.axlero.logstream.grpc.LogEntry request,
        io.grpc.stub.StreamObserver<com.axlero.logstream.grpc.LogAck> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSendLogMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service LogIngestionService.
   */
  public static abstract class LogIngestionServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return LogIngestionServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service LogIngestionService.
   */
  public static final class LogIngestionServiceStub
      extends io.grpc.stub.AbstractAsyncStub<LogIngestionServiceStub> {
    private LogIngestionServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected LogIngestionServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new LogIngestionServiceStub(channel, callOptions);
    }

    /**
     */
    public void sendLog(com.axlero.logstream.grpc.LogEntry request,
        io.grpc.stub.StreamObserver<com.axlero.logstream.grpc.LogAck> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSendLogMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service LogIngestionService.
   */
  public static final class LogIngestionServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<LogIngestionServiceBlockingStub> {
    private LogIngestionServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected LogIngestionServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new LogIngestionServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.axlero.logstream.grpc.LogAck sendLog(com.axlero.logstream.grpc.LogEntry request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSendLogMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service LogIngestionService.
   */
  public static final class LogIngestionServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<LogIngestionServiceFutureStub> {
    private LogIngestionServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected LogIngestionServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new LogIngestionServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.axlero.logstream.grpc.LogAck> sendLog(
        com.axlero.logstream.grpc.LogEntry request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSendLogMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_SEND_LOG = 0;

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
        case METHODID_SEND_LOG:
          serviceImpl.sendLog((com.axlero.logstream.grpc.LogEntry) request,
              (io.grpc.stub.StreamObserver<com.axlero.logstream.grpc.LogAck>) responseObserver);
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
          getSendLogMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.axlero.logstream.grpc.LogEntry,
              com.axlero.logstream.grpc.LogAck>(
                service, METHODID_SEND_LOG)))
        .build();
  }

  private static abstract class LogIngestionServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    LogIngestionServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.axlero.logstream.grpc.LogService.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("LogIngestionService");
    }
  }

  private static final class LogIngestionServiceFileDescriptorSupplier
      extends LogIngestionServiceBaseDescriptorSupplier {
    LogIngestionServiceFileDescriptorSupplier() {}
  }

  private static final class LogIngestionServiceMethodDescriptorSupplier
      extends LogIngestionServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    LogIngestionServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (LogIngestionServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new LogIngestionServiceFileDescriptorSupplier())
              .addMethod(getSendLogMethod())
              .build();
        }
      }
    }
    return result;
  }
}
