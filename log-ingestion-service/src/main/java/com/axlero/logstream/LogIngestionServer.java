package com.axlero.logstream;

import com.axlero.logstream.grpc.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

// This is the "post office" - it listens on port 9090 and accepts
// log messages sent to it from other services.
public class LogIngestionServer {

    public static void main(String[] args) throws Exception {
        Server server = ServerBuilder.forPort(9090)
                .addService(new LogServiceImpl())
                .build();

        server.start();
        System.out.println("LogStream gRPC server started on port 9090");
        server.awaitTermination();
    }

    static class LogServiceImpl extends LogIngestionServiceGrpc.LogIngestionServiceImplBase {
        @Override
        public void sendLog(LogEntry request, StreamObserver<LogAck> responseObserver) {
            // Week 2 will plug Lucene indexing in right here.
            // For now, we just print it to prove the pipe works end to end.
            System.out.println("[" + request.getLevel() + "] "
                    + request.getServiceName() + ": " + request.getMessage());

            LogAck ack = LogAck.newBuilder().setReceived(true).build();
            responseObserver.onNext(ack);
            responseObserver.onCompleted();
        }
    }
}
