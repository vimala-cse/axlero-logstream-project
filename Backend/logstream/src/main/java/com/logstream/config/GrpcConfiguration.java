package com.logstream.config;

import com.logstream.grpc.GrpcServer;
import com.logstream.grpc.LogServiceImpl;
import com.logstream.lucene.LuceneIndexService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcConfiguration {


    @Bean
    public LuceneIndexService luceneIndexService() {

        return new LuceneIndexService(
                "./data/lucene-index"
        );
    }


    @Bean
    public LogServiceImpl logServiceImpl(
            LuceneIndexService indexService) {

        return new LogServiceImpl(
                indexService
        );
    }


    @Bean(
            initMethod = "start",
            destroyMethod = "stop"
    )
    public GrpcServer grpcServer(
            LogServiceImpl logService) {

        return new GrpcServer(
                9090,
                logService
        );
    }
}
