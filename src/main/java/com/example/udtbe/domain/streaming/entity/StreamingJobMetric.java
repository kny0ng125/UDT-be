package com.example.udtbe.domain.streaming.entity;


import static lombok.AccessLevel.PRIVATE;

import com.example.udtbe.domain.streaming.entity.enums.StreamingJobStatus;
import com.example.udtbe.domain.streaming.entity.enums.StreamingJobType;
import com.example.udtbe.global.entity.TimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Entity
@Getter
@Table(name = "batch_job_metric")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StreamingJobMetric extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "batch_job_metric_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    private StreamingJobType type;

    @Enumerated(EnumType.STRING)
    private StreamingJobStatus status;

    private long totalRead = 0;
    private long totalComplete = 0;
    private long totalInvalid = 0;
    private long totalFailed = 0;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @Builder(access = PRIVATE)
    private StreamingJobMetric(StreamingJobType type, StreamingJobStatus status, LocalDateTime startTime,
            LocalDateTime endTime) {
        this.type = type;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public static StreamingJobMetric of(StreamingJobType type, StreamingJobStatus status,
            LocalDateTime startTime, LocalDateTime endTime) {

        return StreamingJobMetric.builder()
                .type(type)
                .status(status)
                .startTime(startTime)
                .endTime(endTime)
                .build();
    }

    public void update(StreamingJobStatus status, long totalRead, long totalComplete, long totalInvalid,
            long totalFailed, LocalDateTime startTime, LocalDateTime endTime) {
        this.status = status;
        this.totalRead = totalRead;
        this.totalComplete = totalComplete;
        this.totalInvalid = totalInvalid;
        this.totalFailed = totalFailed;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public void updateEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

}
