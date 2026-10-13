package com.axlero.logstream;

import com.axlero.logstream.grpc.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

public class LogIngestionServer {

    public static void main(String[] args) throws Exception {
        // NEW: start the Live Tail WebSocket server (port 8081) before
        // the gRPC server, so it's ready to accept browser connections
        // as soon as the first log arrives.
        LiveTailWebSocketServer liveTail = new LiveTailWebSocketServer(8081);
        liveTail.start();

        Server server = ServerBuilder.forPort(9090)
                .addService(new LogServiceImpl(liveTail))
                .build();

        server.start();
        System.out.println("LogStream gRPC server started on port 9090");
        server.awaitTermination();
    }

    static class LogServiceImpl extends LogIngestionServiceGrpc.LogIngestionServiceImplBase {

        private final LuceneIndexer indexer;
        private final LiveTailWebSocketServer liveTail;

        LogServiceImpl(LiveTailWebSocketServer liveTail) {
            this.liveTail = liveTail;
            try {
                indexer = new LuceneIndexer();
            } catch (Exception e) {
                throw new RuntimeException("Failed to start Lucene indexer", e);
            }
        }

        @Override
        public void sendLog(LogEntry request, StreamObserver<LogAck> responseObserver) {
            String formattedLine = "[" + request.getLevel() + "] "
                    + request.getServiceName() + ": " + request.getMessage()
                    + " (time: " + request.getTimestamp() + ")";

            System.out.println(formattedLine);

            // Save this log so it becomes searchable later (Week 2).
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

            // NEW: push this log straight to any connected Live Tail
            // browsers, right now - this does NOT go through Lucene at
            // all, which is why it shows up with no search delay.
            liveTail.broadcast(formattedLine);

            LogAck ack = LogAck.newBuilder().setReceived(true).build();
            responseObserver.onNext(ack);
            responseObserver.onCompleted();
        }
    }
}
