package com.example.udtbe.domain.admin.dto.response;

import com.example.udtbe.domain.admin.dto.common.AdminCategoryDTO;
import com.example.udtbe.domain.admin.dto.common.AdminPlatformDTO;
import com.example.udtbe.domain.streaming.dto.JobValidationError;
import com.example.udtbe.domain.streaming.entity.enums.StreamingStatus;
import java.time.LocalDateTime;
import java.util.List;

public record AdminContentRegJobGetDetailResponse(

        Long streamingJobMetricId,

        StreamingStatus status,

        String title,

        String description,

        String posterUrl,

        String backdropUrl,

        String trailerUrl,

        LocalDateTime openDate,

        int runningTime,

        int episode,

        String rating,

        List<AdminCategoryDTO> categories,

        List<String> countries,

        List<Long> directors,

        List<Long> casts,

        List<AdminPlatformDTO> platforms,

        String errorMessage,

        List<JobValidationError> validationErrors,

        int retryCount,

        int skipCount

) {

}