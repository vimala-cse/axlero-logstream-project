package com.logstream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LogstreamApplication {

	public static void main(String[] args) {
		SpringApplication.run(LogstreamApplication.class, args);
		System.out.println(
                "========================================"
        );

        System.out.println(
                "       LOGSTREAM APPLICATION STARTED"
        );

        System.out.println(
                "       Java Version : 21"
        );

        System.out.println(
                "       gRPC Port    : 9090"
        );

        System.out.println(
                "========================================"
        );
    }
}
