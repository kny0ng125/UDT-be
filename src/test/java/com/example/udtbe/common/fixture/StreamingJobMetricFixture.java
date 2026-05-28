package com.example.udtbe.common.fixture;

import com.example.udtbe.domain.streaming.entity.StreamingJobMetric;
import com.example.udtbe.domain.streaming.entity.enums.StreamingJobStatus;
import com.example.udtbe.domain.streaming.entity.enums.StreamingJobType;
import java.time.LocalDateTime;

public class StreamingJobMetricFixture {

    public static StreamingJobMetric completedJob(Long id, StreamingJobType streamingJobType,
            long totalRead) {

        StreamingJobMetric streamingJobMetric = StreamingJobMetric.of(streamingJobType, StreamingJobStatus.NOOP,
                LocalDateTime.now(), LocalDateTime.now());

        streamingJobMetric.update(StreamingJobStatus.COMPLETED, totalRead, totalRead, 0, 0,
                LocalDateTime.now(), LocalDateTime.now().plusHours(1));

        return streamingJobMetric;
    }

    public static StreamingJobMetric failedJob(Long id, StreamingJobType streamingJobType,
            long totalRead) {

        StreamingJobMetric streamingJobMetric = StreamingJobMetric.of(streamingJobType, StreamingJobStatus.NOOP,
                LocalDateTime.now(), LocalDateTime.now());

        streamingJobMetric.update(StreamingJobStatus.COMPLETED, totalRead, 0, 0, totalRead,
                LocalDateTime.now(), LocalDateTime.now().plusHours(1));

        return streamingJobMetric;
    }

    public static StreamingJobMetric partialCompetedJob(Long id, StreamingJobType streamingJobType,
            long totalRead, long totalFailed) {
        StreamingJobMetric streamingJobMetric = StreamingJobMetric.of(streamingJobType, StreamingJobStatus.NOOP,
                LocalDateTime.now(), LocalDateTime.now());

        streamingJobMetric.update(StreamingJobStatus.PARTIAL_COMPLETED, totalRead, totalRead - totalFailed,
                0, totalFailed, LocalDateTime.now(), LocalDateTime.now().plusHours(1));

        return streamingJobMetric;
    }
}
