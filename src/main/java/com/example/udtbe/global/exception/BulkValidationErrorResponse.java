package com.example.udtbe.global.exception;

import com.example.udtbe.domain.streaming.dto.JobValidationError;
import java.util.List;

public record BulkValidationErrorResponse(
        String code,
        String message,
        Long jobId,
        List<JobValidationError> errors
) {

}
