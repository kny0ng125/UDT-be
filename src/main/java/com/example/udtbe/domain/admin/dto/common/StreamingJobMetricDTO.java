package com.example.udtbe.domain.admin.dto.common;

public record StreamingJobMetricDTO(
        long totalRead,
        long totalCompleted,
        long totalInvalid,
        long totalFailed
) {

}
