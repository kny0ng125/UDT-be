package com.example.udtbe.domain.streaming.repository;

import com.example.udtbe.domain.streaming.entity.AdminContentDeleteJob;
import com.example.udtbe.domain.streaming.entity.enums.StreamingStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminContentDeleteJobRepository extends
        JpaRepository<AdminContentDeleteJob, Long> {


    List<AdminContentDeleteJob> findByStatus(StreamingStatus status);

    List<AdminContentDeleteJob> findByStatusAndRetryCountLessThan(StreamingStatus status,
            int retryCount);

    List<AdminContentDeleteJob> findByStatusIn(List<StreamingStatus> statuses);

    void deleteByStatus(StreamingStatus status);

    long countByStatus(StreamingStatus status);
}
