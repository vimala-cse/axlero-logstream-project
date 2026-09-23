package com.logstream.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogRecord {

    private String timestamp;

    private String level;

    private String service;

    private String message;

    private String traceId;
    
    private long responseTime;
}
