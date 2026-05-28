package com.example.udtbe.common.fixture;


import com.example.udtbe.domain.streaming.entity.AdminContentDeleteJob;
import com.example.udtbe.domain.streaming.entity.enums.StreamingStatus;

public class AdminContentDeleteJobFixture {

    public static AdminContentDeleteJob createPendingJob(Long memberId, Long contentId) {

        return AdminContentDeleteJob.of(StreamingStatus.PENDING, memberId, contentId);
    }
}