package com.axlero.logstream;

import com.axlero.logstream.grpc.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

public class LogIngestionServer {

    public static void main(String[] args) throws Exception {
        // NEW: start the Live Tail WebSocket server on its own port
        // (8081). The React dashboard connects to this directly for
        // Live Tail - separate from the REST API (port 8080) and the
        // gRPC ingestion server (port 9090) below.
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

        // NEW for Week 2: one shared indexer for saving logs into Lucene.
        private final LuceneIndexer indexer;

        // NEW for Live Tail: lets us push every incoming log straight
        // to connected browsers, without touching the Lucene index.
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

            // NEW for Live Tail: push this same log straight to every
            // connected browser tab, right now - this is the "bypassing
            // the search index" part from the project guide. The
            // browser gets it the instant it arrives, not by asking
            // Lucene "anything new?" every couple of seconds.
            String line = "[" + request.getLevel() + "] "
                    + request.getServiceName() + ": " + request.getMessage()
                    + " (time: " + request.getTimestamp() + ")";
            liveTail.broadcast(line);

            LogAck ack = LogAck.newBuilder().setReceived(true).build();
            responseObserver.onNext(ack);
            responseObserver.onCompleted();
        }
    }
}
