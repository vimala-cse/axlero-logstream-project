package com.axlero.logstream;

import com.axlero.logstream.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

// Sends a bigger, more varied batch of sample logs than TestClient does,
// so AggregationDemo has enough data to show a meaningful chart.
// Run this ONCE (right after starting LogIngestionServer), then run
// AggregationDemo to see the counts.
public class BulkTestClient {
    public static void main(String[] args) {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("localhost", 9090)
                .usePlaintext()
                .build();

        LogIngestionServiceGrpc.LogIngestionServiceBlockingStub stub =
                LogIngestionServiceGrpc.newBlockingStub(channel);

        sendLog(stub, "payment-service", "ERROR", "Connection timeout");
        sendLog(stub, "payment-service", "ERROR", "Payment gateway timeout");
        sendLog(stub, "payment-service", "INFO", "Payment processed successfully");
        sendLog(stub, "auth-service", "INFO", "User login successful");
        sendLog(stub, "auth-service", "INFO", "User logout successful");
        sendLog(stub, "auth-service", "WARN", "Repeated failed login attempt");
        sendLog(stub, "billing-api", "ERROR", "Database query failed");
        sendLog(stub, "billing-api", "INFO", "Invoice generated");
        sendLog(stub, "inventory-service", "WARN", "Stock running low");
        sendLog(stub, "inventory-service", "INFO", "Stock updated");
        sendLog(stub, "inventory-service", "ERROR", "Warehouse sync failed");
        sendLog(stub, "notification-service", "INFO", "Email sent");
        sendLog(stub, "notification-service", "WARN", "SMS delivery delayed");
        sendLog(stub, "order-service", "INFO", "Order placed");
        sendLog(stub, "order-service", "ERROR", "Order validation failed");

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
        System.out.println("Sent [" + level + "] " + service + ": " + message + " -> acknowledged: " + ack.getReceived());
    }
}
