package com.logstream.client;

import com.logstream.grpc.LogMessage;
import com.logstream.grpc.LogResponse;
import com.logstream.grpc.LogServiceGrpc;
import com.logstream.grpc.SearchRequest;
import com.logstream.grpc.SearchResponse;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class MockLogClient {

    public static void main(String[] args) {

        ManagedChannel channel =
                ManagedChannelBuilder
                        .forAddress(
                                "localhost",
                                9090
                        )
                        .usePlaintext()
                        .build();

        try {

            LogServiceGrpc.LogServiceBlockingStub stub =
                    LogServiceGrpc.newBlockingStub(channel);

            System.out.println(
                    "========================================"
            );
            System.out.println(
                    "        WEEK 2 - LUCENE SEARCH"
            );
            System.out.println(
                    "========================================"
            );

            sendLog(
                    stub,
                    "ERROR",
                    "billing-api",
                    "Database query timeout",
                    "trace-1001",
                    1500
            );

            sendLog(
                    stub,
                    "INFO",
                    "billing-api",
                    "Invoice generated successfully",
                    "trace-1002",
                    200
            );

            sendLog(
                    stub,
                    "ERROR",
                    "auth-service",
                    "Database connection refused",
                    "trace-1003",
                    1200
            );

            sendLog(
                    stub,
                    "WARN",
                    "payment-api",
                    "Payment response is slow",
                    "trace-1004",
                    1800
            );

            sendLog(
                    stub,
                    "INFO",
                    "catalog-api",
                    "Product cache refreshed",
                    "trace-1005",
                    100
            );

            search(stub, "ERROR");

            search(stub, "billing-api");

            search(
                    stub,
                    "level:ERROR AND service:billing-api"
            );

            search(
                    stub,
                    "level:ERROR AND service:billing-api AND response_time > 1000"
            );

        } finally {

            channel.shutdown();
        }
    }

    private static void sendLog(
            LogServiceGrpc.LogServiceBlockingStub stub,
            String level,
            String service,
            String message,
            String traceId,
            long responseTime) {

        LogMessage log =
                LogMessage.newBuilder()
                        .setTimestamp(
                                "2026-09-19T10:30:00Z"
                        )
                        .setLevel(level)
                        .setService(service)
                        .setMessage(message)
                        .setTraceId(traceId)
                        .setResponseTime(responseTime)
                        .build();

        LogResponse response =
                stub.sendLog(log);

        System.out.println(
                "CLIENT -> "
                + level
                + " | "
                + service
                + " | "
                + response.getMessage()
        );
    }

    private static void search(
            LogServiceGrpc.LogServiceBlockingStub stub,
            String query) {

        System.out.println();
        System.out.println(
                "----------------------------------------"
        );

        System.out.println(
                "SEARCH QUERY : " + query
        );

        SearchRequest request =
                SearchRequest.newBuilder()
                        .setQuery(query)
                        .setLimit(50)
                        .build();

        SearchResponse response =
                stub.searchLogs(request);

        System.out.println(
                "RESULT COUNT : "
                + response.getLogsCount()
        );

        for (LogMessage log :
                response.getLogsList()) {

            System.out.println(
                    log.getLevel()
                    + " | "
                    + log.getService()
                    + " | "
                    + log.getMessage()
                    + " | response_time="
                    + log.getResponseTime()
            );
        }
    }
}