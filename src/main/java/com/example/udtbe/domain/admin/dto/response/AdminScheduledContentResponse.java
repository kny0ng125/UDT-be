package com.example.udtbe.domain.admin.dto.response;

import com.example.udtbe.domain.streaming.entity.enums.StreamingJobType;
import com.example.udtbe.domain.streaming.entity.enums.StreamingStatus;
import java.time.LocalDateTime;

public record AdminScheduledContentResponse(
        Long id,
        StreamingStatus status,
        Long memberId,
        LocalDateTime createdAt,
        LocalDateTime scheduledAt,
        LocalDateTime finishedAt,
        StreamingJobType jobType
) {

}
