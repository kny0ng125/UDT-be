package com.example.udtbe.global.exception;

import lombok.Getter;

@Getter
public class StreamingFailureException extends RuntimeException {

    private final Long jobId;

    public StreamingFailureException(Long jobId, String errorMessage) {
        super(errorMessage);
        this.jobId = jobId;
    }
}
