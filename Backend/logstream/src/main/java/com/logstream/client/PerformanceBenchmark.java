package com.logstream.client;

import com.logstream.grpc.LogMessage;
import com.logstream.grpc.LogResponse;
import com.logstream.grpc.LogServiceGrpc;
import com.logstream.grpc.SearchRequest;
import com.logstream.grpc.SearchResponse;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PerformanceBenchmark {

    private static final int TOTAL_LOGS = 10_000;

    public static void main(String[] args)
            throws Exception {

        ManagedChannel channel =
                ManagedChannelBuilder
                        .forAddress(
                                "localhost",
                                9090
                        )
                        .usePlaintext()
                        .build();

        try {

            LogServiceGrpc.LogServiceStub asyncStub =
                    LogServiceGrpc.newStub(channel);

            System.out.println();
            System.out.println(
                    "========================================"
            );
            System.out.println(
                    "      MID-PROJECT PERFORMANCE TEST"
            );
            System.out.println(
                    "========================================"
            );

            System.out.println(
                    "TOTAL LOGS : "
                    + TOTAL_LOGS
            );

            CountDownLatch finishLatch =
                    new CountDownLatch(1);

            final long startTime =
                    System.nanoTime();

            StreamObserver<LogMessage> requestObserver =
                    asyncStub.sendLogs(
                            new StreamObserver<LogResponse>() {

                                @Override
                                public void onNext(
                                        LogResponse response) {

                                    long endTime =
                                            System.nanoTime();

                                    double seconds =
                                            (endTime
                                                    - startTime)
                                                    / 1_000_000_000.0;

                                    double throughput =
                                            TOTAL_LOGS
                                                    / seconds;

                                    System.out.println();

                                    System.out.println(
                                            "========================================"
                                    );

                                    System.out.println(
                                            "       INGESTION BENCHMARK"
                                    );

                                    System.out.println(
                                            "========================================"
                                    );

                                    System.out.println(
                                            "LOGS       : "
                                            + TOTAL_LOGS
                                    );

                                    System.out.println(
                                            "TIME       : "
                                            + String.format(
                                                    "%.3f sec",
                                                    seconds
                                            )
                                    );

                                    System.out.println(
                                            "THROUGHPUT : "
                                            + String.format(
                                                    "%.2f logs/sec",
                                                    throughput
                                            )
                                    );

                                    if (throughput >= 10000) {

                                        System.out.println(
                                                "TARGET     : ACHIEVED"
                                        );

                                    } else {

                                        System.out.println(
                                                "TARGET     : BELOW 10,000 logs/sec"
                                        );
                                    }

                                    finishLatch.countDown();
                                }

                                @Override
                                public void onError(
                                        Throwable throwable) {

                                    System.out.println(
                                            "BENCHMARK ERROR : "
                                            + throwable.getMessage()
                                    );

                                    finishLatch.countDown();
                                }

                                @Override
                                public void onCompleted() {
                                }
                            }
                    );

            for (int i = 1;
                 i <= TOTAL_LOGS;
                 i++) {

                LogMessage log =
                        createLog(i);

                requestObserver.onNext(log);
            }

            requestObserver.onCompleted();

            finishLatch.await(
                    10,
                    TimeUnit.MINUTES
            );

            Thread.sleep(2000);

            runSearchBenchmark(
                    asyncStub
            );

        } finally {

            channel.shutdown();
        }
    }

    private static LogMessage createLog(
            int number) {

        String level;

        if (number % 10 == 0) {
            level = "ERROR";
        } else if (number % 5 == 0) {
            level = "WARN";
        } else {
            level = "INFO";
        }

        String service;

        if (number % 3 == 0) {
            service = "billing-api";
        } else if (number % 3 == 1) {
            service = "auth-service";
        } else {
            service = "payment-api";
        }

        long responseTime =
                (number % 2000) + 100;

        String message;

        if (level.equals("ERROR")) {

            message =
                    "Database query timeout "
                    + number;

        } else {

            message =
                    "Request processed successfully "
                    + number;
        }

        return LogMessage.newBuilder()
                .setTimestamp(
                        "2026-09-19T10:30:00Z"
                )
                .setLevel(level)
                .setService(service)
                .setMessage(message)
                .setTraceId(
                        "benchmark-" + number
                )
                .setResponseTime(
                        responseTime
                )
                .build();
    }

    private static void runSearchBenchmark(
            LogServiceGrpc.LogServiceStub stub)
            throws Exception {

        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println(
                "       SEARCH PERFORMANCE TEST"
        );

        System.out.println(
                "========================================"
        );

        SearchRequest request =
                SearchRequest.newBuilder()
                        .setQuery(
                                "level:ERROR AND service:billing-api"
                        )
                        .setLimit(50)
                        .build();

        CountDownLatch latch =
                new CountDownLatch(1);

        final long start =
                System.nanoTime();

        stub.searchLogs(
                request,
                new StreamObserver<SearchResponse>() {

                    @Override
                    public void onNext(
                            SearchResponse response) {

                        long end =
                                System.nanoTime();

                        double milliseconds =
                                (end - start)
                                        / 1_000_000.0;

                        System.out.println(
                                "RESULTS : "
                                + response.getLogsCount()
                        );

                        System.out.println(
                                "SEARCH TIME : "
                                + String.format(
                                        "%.3f ms",
                                        milliseconds
                                )
                        );

                        if (milliseconds < 50) {

                            System.out.println(
                                    "TARGET : < 50 ms ACHIEVED"
                            );

                        } else {

                            System.out.println(
                                    "TARGET : ABOVE 50 ms"
                            );
                        }
                    }

                    @Override
                    public void onError(
                            Throwable throwable) {

                        System.out.println(
                                "SEARCH ERROR : "
                                + throwable.getMessage()
                        );

                        latch.countDown();
                    }

                    @Override
                    public void onCompleted() {

                        latch.countDown();
                    }
                }
        );

        latch.await(
                60,
                TimeUnit.SECONDS
        );
    }
}
