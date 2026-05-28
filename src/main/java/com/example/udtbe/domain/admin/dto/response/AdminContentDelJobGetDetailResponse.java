package com.example.udtbe.domain.admin.dto.response;

import com.example.udtbe.domain.streaming.dto.JobValidationError;
import com.example.udtbe.domain.streaming.entity.enums.StreamingStatus;
import java.util.List;

public record AdminContentDelJobGetDetailResponse(

        Long streamingJobMetricId,

        StreamingStatus status,

        Long contentId,

        String errorMessage,

        List<JobValidationError> validationErrors,

        int retryCount,

        int skipCount

) {

}
