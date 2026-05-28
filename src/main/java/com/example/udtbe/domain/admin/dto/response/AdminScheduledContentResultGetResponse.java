package com.example.udtbe.domain.admin.dto.response;

import com.example.udtbe.domain.streaming.entity.enums.StreamingJobStatus;
import com.example.udtbe.domain.streaming.entity.enums.StreamingJobType;
import java.time.LocalDateTime;

public record AdminScheduledContentResultGetResponse(

        Long resultId,
        StreamingJobType type,
        StreamingJobStatus status,
        long totalRead,
        long totalCompleted,
        long totalInvalid,
        long totalFailed,
        LocalDateTime startTime,
        LocalDateTime endTime
) {

}
