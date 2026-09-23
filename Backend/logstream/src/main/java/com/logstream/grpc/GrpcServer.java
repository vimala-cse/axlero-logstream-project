package com.logstream.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class GrpcServer {

    private final int port;

    private final LogServiceImpl logService;

    private Server server;


    public GrpcServer(
            int port,
            LogServiceImpl logService) {

        this.port = port;

        this.logService =
                logService;
    }


    public void start()
            throws IOException {

        server =
                ServerBuilder
                        .forPort(port)

                        .addService(
                                logService
                        )

                        .build();


        server.start();


        System.out.println(
                "========================================"
        );

        System.out.println(
                "       MANUAL gRPC SERVER STARTED"
        );

        System.out.println(
                "       Port : " + port
        );

        System.out.println(
                "========================================"
        );


        Runtime.getRuntime()
                .addShutdownHook(

                        new Thread(() -> {

                            try {

                                stop();

                            } catch (
                                    InterruptedException e) {

                                Thread
                                        .currentThread()
                                        .interrupt();
                            }

                        })
                );
    }


    public void blockUntilShutdown()
            throws InterruptedException {

        if (server != null) {

            server.awaitTermination();
        }
    }


    public void stop()
            throws InterruptedException {

        if (server != null) {

            server.shutdown();

            server.awaitTermination(
                    30,
                    TimeUnit.SECONDS
            );
        }
    }
}
