package com.example.udtbe.domain.batch.dto;

public record JobValidationError(
        String field,
        String value,
        String code,
        String message
) {

}
