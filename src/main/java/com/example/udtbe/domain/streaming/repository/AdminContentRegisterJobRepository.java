package com.example.udtbe.domain.streaming.repository;


import com.example.udtbe.domain.streaming.entity.AdminContentRegisterJob;
import com.example.udtbe.domain.streaming.entity.enums.StreamingStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminContentRegisterJobRepository extends
        JpaRepository<AdminContentRegisterJob, Long> {


    List<AdminContentRegisterJob> findByStatus(StreamingStatus status);

    List<AdminContentRegisterJob> findByStatusAndRetryCountLessThan(StreamingStatus status,
            int retryCount);

    List<AdminContentRegisterJob> findByStatusIn(List<StreamingStatus> statuses);

    void deleteByStatus(StreamingStatus status);

    long countByStatus(StreamingStatus status);
}
