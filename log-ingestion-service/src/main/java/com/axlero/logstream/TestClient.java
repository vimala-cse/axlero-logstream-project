package com.axlero.logstream;

import com.axlero.logstream.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

// Sends a few different sample logs, so we have some variety to
// search through in SearchDemo.
public class TestClient {
    public static void main(String[] args) {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("localhost", 9090)
                .usePlaintext()
                .build();

        LogIngestionServiceGrpc.LogIngestionServiceBlockingStub stub =
                LogIngestionServiceGrpc.newBlockingStub(channel);

        sendLog(stub, "payment-service", "ERROR", "Connection timeout");
        sendLog(stub, "auth-service", "INFO", "User login successful");
        sendLog(stub, "billing-api", "ERROR", "Database query failed");
        sendLog(stub, "inventory-service", "WARN", "Stock running low");
        sendLog(stub, "payment-service", "ERROR", "Payment gateway timeout");

        channel.shutdown();
    }

    private static void sendLog(LogIngestionServiceGrpc.LogIngestionServiceBlockingStub stub,
                                 String service, String level, String message) {
        LogEntry entry = LogEntry.newBuilder()
                .setServiceName(service)
                .setLevel(level)
                .setMessage(message)
                .setTimestamp(System.currentTimeMillis())
                .build();

        LogAck ack = stub.sendLog(entry);
        System.out.println("Sent [" + level + "] " + message + " -> acknowledged: " + ack.getReceived());
    }
}
