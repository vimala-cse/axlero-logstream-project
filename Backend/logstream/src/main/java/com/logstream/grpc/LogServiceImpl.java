package com.logstream.grpc;

import com.logstream.exception.LogValidationException;
import com.logstream.lucene.LuceneIndexService;
import com.logstream.model.LogRecord;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.List;

public class LogServiceImpl
        extends LogServiceGrpc.LogServiceImplBase {

    private final LuceneIndexService indexService;

    public LogServiceImpl(
            LuceneIndexService indexService) {

        this.indexService = indexService;
    }

    @Override
    public void sendLog(
            LogMessage request,
            StreamObserver<LogResponse> responseObserver) {

        try {

            validate(request);

            LogRecord record =
                    convert(request);

            indexService.index(record);

            responseObserver.onNext(
                    LogResponse.newBuilder()
                            .setSuccess(true)
                            .setMessage(
                                    "Log accepted successfully"
                            )
                            .build()
            );

            responseObserver.onCompleted();

        } catch (LogValidationException e) {

            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription(e.getMessage())
                            .asRuntimeException()
            );

        } catch (Exception e) {

            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription(
                                    "Unable to index log"
                            )
                            .withCause(e)
                            .asRuntimeException()
            );
        }
    }

    @Override
    public StreamObserver<LogMessage> sendLogs(
            StreamObserver<LogResponse> responseObserver) {

        return new StreamObserver<>() {

            private final List<LogRecord> batch =
                    new ArrayList<>();

            private long received = 0;

            @Override
            public void onNext(LogMessage message) {

                try {

                    validate(message);

                    batch.add(convert(message));

                    received++;

                    if (batch.size() >= 1000) {

                        indexService.indexBatch(
                                new ArrayList<>(batch)
                        );

                        batch.clear();
                    }

                } catch (Exception e) {

                    System.out.println(
                            "STREAM ERROR -> "
                            + e.getMessage()
                    );
                }
            }

            @Override
            public void onError(Throwable throwable) {

                System.out.println(
                        "gRPC STREAM ERROR -> "
                        + throwable.getMessage()
                );
            }

            @Override
            public void onCompleted() {

                try {

                    if (!batch.isEmpty()) {

                        indexService.indexBatch(
                                new ArrayList<>(batch)
                        );

                        batch.clear();
                    }

                    responseObserver.onNext(
                            LogResponse.newBuilder()
                                    .setSuccess(true)
                                    .setMessage(
                                            "Received "
                                            + received
                                            + " logs"
                                    )
                                    .build()
                    );

                    responseObserver.onCompleted();

                    System.out.println(
                            "STREAM COMPLETE -> "
                            + received
                            + " logs"
                    );

                } catch (Exception e) {

                    responseObserver.onError(
                            Status.INTERNAL
                                    .withDescription(
                                            e.getMessage()
                                    )
                                    .withCause(e)
                                    .asRuntimeException()
                    );
                }
            }
        };
    }

    @Override
    public void searchLogs(
            SearchRequest request,
            StreamObserver<SearchResponse> responseObserver) {

        try {

            System.out.println();
            System.out.println(
                    "========================================"
            );
            System.out.println("LUCENE SEARCH");
            System.out.println(
                    "QUERY : " + request.getQuery()
            );
            System.out.println(
                    "========================================"
            );

            List<LogRecord> results =
                    indexService.search(
                            request.getQuery(),
                            request.getLimit()
                    );

            SearchResponse.Builder response =
                    SearchResponse.newBuilder();

            for (LogRecord record : results) {

                response.addLogs(
                        LogMessage.newBuilder()
                                .setTimestamp(
                                        record.getTimestamp()
                                )
                                .setLevel(
                                        record.getLevel()
                                )
                                .setService(
                                        record.getService()
                                )
                                .setMessage(
                                        record.getMessage()
                                )
                                .setTraceId(
                                        record.getTraceId()
                                )
                                .setResponseTime(
                                        record.getResponseTime()
                                )
                                .build()
                );
            }

            responseObserver.onNext(
                    response.build()
            );

            responseObserver.onCompleted();

            System.out.println(
                    "SEARCH RESULTS : "
                    + results.size()
            );

        } catch (Exception e) {

            System.out.println(
                    "SEARCH ERROR : "
                    + e.getMessage()
            );

            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription(
                                    e.getMessage()
                            )
                            .withCause(e)
                            .asRuntimeException()
            );
        }
    }

    private LogRecord convert(LogMessage request) {

        return LogRecord.builder()
                .timestamp(request.getTimestamp())
                .level(
                        request.getLevel()
                                .toUpperCase()
                )
                .service(request.getService())
                .message(request.getMessage())
                .traceId(request.getTraceId())
                .responseTime(
                        request.getResponseTime()
                )
                .build();
    }

    private void validate(LogMessage request) {

        if (request == null) {
            throw new LogValidationException(
                    "Log request cannot be null"
            );
        }

        if (request.getTimestamp().isBlank()) {
            throw new LogValidationException(
                    "Timestamp is required"
            );
        }

        if (request.getLevel().isBlank()) {
            throw new LogValidationException(
                    "Level is required"
            );
        }

        if (request.getService().isBlank()) {
            throw new LogValidationException(
                    "Service is required"
            );
        }

        if (request.getMessage().isBlank()) {
            throw new LogValidationException(
                    "Message is required"
            );
        }
    }
}