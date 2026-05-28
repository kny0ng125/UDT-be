package com.example.udtbe.domain.streaming.repository;


import com.example.udtbe.domain.streaming.entity.AdminContentUpdateJob;
import com.example.udtbe.domain.streaming.entity.enums.StreamingStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminContentUpdateJobRepository extends
        JpaRepository<AdminContentUpdateJob, Long> {


    List<AdminContentUpdateJob> findByStatus(StreamingStatus status);

    List<AdminContentUpdateJob> findByStatusAndRetryCountLessThan(StreamingStatus status,
            int retryCount);

    List<AdminContentUpdateJob> findByStatusIn(List<StreamingStatus> statuses);

    void deleteByStatus(StreamingStatus status);

    long countByStatus(StreamingStatus status);
}
