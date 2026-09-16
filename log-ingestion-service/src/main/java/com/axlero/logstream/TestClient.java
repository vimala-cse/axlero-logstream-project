package com.axlero.logstream;

import com.axlero.logstream.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

// This simulates a microservice sending a log to our server.
// Run this AFTER LogIngestionServer is already running.
public class TestClient {
    public static void main(String[] args) {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("localhost", 9090)
                .usePlaintext()
                .build();

        LogIngestionServiceGrpc.LogIngestionServiceBlockingStub stub =
                LogIngestionServiceGrpc.newBlockingStub(channel);

        LogEntry entry = LogEntry.newBuilder()
                .setServiceName("payment-service")
                .setLevel("ERROR")
                .setMessage("Connection timeout")
                .setTimestamp(System.currentTimeMillis())
                .build();

        LogAck ack = stub.sendLog(entry);
        System.out.println("Server acknowledged: " + ack.getReceived());

        channel.shutdown();
    }
}
