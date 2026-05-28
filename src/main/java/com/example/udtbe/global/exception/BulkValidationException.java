package com.example.udtbe.global.exception;

import com.example.udtbe.domain.streaming.dto.JobValidationError;
import java.util.List;
import lombok.Getter;

@Getter
public class BulkValidationException extends RuntimeException {

    private final Long jobId;
    private final List<JobValidationError> errors;

    public BulkValidationException(Long jobId, List<JobValidationError> errors) {
        super("validation failed with " + errors.size() + " error(s)");
        this.jobId = jobId;
        this.errors = errors;
    }
}
