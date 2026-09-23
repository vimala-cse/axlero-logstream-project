package com.logstream.exception;

public class LogStreamException
        extends RuntimeException {

    public LogStreamException(
            String message) {

        super(message);
    }


    public LogStreamException(
            String message,
            Throwable cause) {

        super(
                message,
                cause
        );
    }
}
