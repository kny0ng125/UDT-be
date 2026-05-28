package com.example.udtbe.domain.streaming.repository;

import com.example.udtbe.domain.admin.dto.common.StreamingJobMetricDTO;
import com.example.udtbe.domain.admin.dto.response.AdminScheduledContentMetricGetResponse;
import com.example.udtbe.domain.admin.dto.response.AdminScheduledContentResponse;
import com.example.udtbe.domain.admin.dto.response.AdminScheduledContentResultGetResponse;
import com.example.udtbe.domain.streaming.entity.enums.StreamingFilterType;
import com.example.udtbe.global.dto.CursorPageResponse;

public interface AdminContentJobRepositoryCustom {

    CursorPageResponse<AdminScheduledContentResponse> getJobsByCursor(
            String cursor, int size, StreamingFilterType type);

    StreamingJobMetricDTO getContentRegisterJobMetrics(Long metricId);

    StreamingJobMetricDTO getContentUpdateJobMetrics(Long metricId);

    StreamingJobMetricDTO getContentDeleteJobMetrics(Long metricId);

    AdminScheduledContentMetricGetResponse getScheduledContentMetrics();

    CursorPageResponse<AdminScheduledContentResultGetResponse> getScheduledContentResults(
            String cursor, int size);
}
