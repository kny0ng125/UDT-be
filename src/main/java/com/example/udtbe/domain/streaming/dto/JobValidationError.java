package com.example.udtbe.domain.streaming.dto;

public record JobValidationError(
        String field,
        String value,
        String code,
        String message
) {

}
