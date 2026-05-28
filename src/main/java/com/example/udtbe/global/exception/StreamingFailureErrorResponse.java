package com.example.udtbe.global.exception;

public record StreamingFailureErrorResponse(
        String code,
        String message,
        Long jobId,
        String errorMessage
) {

}
