package com.axlero.logstream;

import com.axlero.logstream.grpc.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

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

        // NEW for Week 2: one shared indexer for saving logs into Lucene.
        private final LuceneIndexer indexer;

        LogServiceImpl() {
            try {
                indexer = new LuceneIndexer();
            } catch (Exception e) {
                throw new RuntimeException("Failed to start Lucene indexer", e);
            }
        }

        @Override
        public void sendLog(LogEntry request, StreamObserver<LogAck> responseObserver) {
            System.out.println("[" + request.getLevel() + "] "
                    + request.getServiceName() + ": " + request.getMessage());

            // NEW for Week 2: save this log so it becomes searchable later.
            try {
                indexer.addLog(
                        request.getServiceName(),
                        request.getLevel(),
                        request.getMessage(),
                        request.getTimestamp()
                );
            } catch (Exception e) {
                System.out.println("Warning: failed to index log - " + e.getMessage());
            }

            LogAck ack = LogAck.newBuilder().setReceived(true).build();
            responseObserver.onNext(ack);
            responseObserver.onCompleted();
        }
    }
}
