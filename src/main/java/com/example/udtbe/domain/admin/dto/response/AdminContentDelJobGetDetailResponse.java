package com.example.udtbe.domain.admin.dto.response;

import com.example.udtbe.domain.batch.dto.JobValidationError;
import com.example.udtbe.domain.batch.entity.enums.BatchStatus;
import java.util.List;

public record AdminContentDelJobGetDetailResponse(

        Long batchJobMetricId,

        BatchStatus status,

        Long contentId,

        String errorCode,

        String errorMessage,

        List<JobValidationError> validationErrors,

        int retryCount,

        int skipCount

) {

}
