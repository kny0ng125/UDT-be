package com.example.udtbe.domain.streaming.repository;

import com.example.udtbe.domain.streaming.entity.StreamingJobMetric;
import com.example.udtbe.domain.streaming.entity.enums.StreamingJobType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StreamingJobMetricRepository extends
        JpaRepository<StreamingJobMetric, Long> {

    @Query("SELECT j FROM StreamingJobMetric j ORDER BY j.id ASC")
    List<StreamingJobMetric> findAllByOrderByIdAsc();

    Optional<StreamingJobMetric> findAdminContentJobMetricByType(StreamingJobType type);
}
