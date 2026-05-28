package com.example.udtbe.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.udtbe.domain.admin.dto.common.AdminCategoryDTO;
import com.example.udtbe.domain.admin.dto.common.AdminPlatformDTO;
import com.example.udtbe.domain.admin.dto.request.AdminContentRegisterRequest;
import com.example.udtbe.domain.admin.dto.request.AdminContentUpdateRequest;
import com.example.udtbe.domain.admin.dto.response.AdminContentDeleteResponse;
import com.example.udtbe.domain.admin.dto.response.AdminContentRegisterResponse;
import com.example.udtbe.domain.admin.dto.response.AdminContentUpdateResponse;
import com.example.udtbe.domain.admin.service.AdminQuery;
import com.example.udtbe.domain.admin.service.AdminService;
import com.example.udtbe.domain.admin.service.ContentTxService;
import com.example.udtbe.domain.admin.service.JobTrackingService;
import com.example.udtbe.domain.admin.service.StreamingJobExecutor;
import com.example.udtbe.domain.admin.service.StreamingJobSpec;
import com.example.udtbe.domain.streaming.dto.JobValidationError;
import com.example.udtbe.domain.streaming.entity.AdminContentDeleteJob;
import com.example.udtbe.domain.streaming.entity.AdminContentRegisterJob;
import com.example.udtbe.domain.streaming.entity.AdminContentUpdateJob;
import com.example.udtbe.domain.streaming.entity.enums.StreamingStatus;
import com.example.udtbe.global.exception.RestApiException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 재제출(P0) 단위 검증: (1) INVALID 가드가 executor 진입 전 선검사로 동작하는지,
 * (2) 조립된 spec이 기존 jobId를 그대로 협력자에 전달하는지.
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceResubmitTest {

    @Mock
    private StreamingJobExecutor streamingJobExecutor;
    @Mock
    private JobTrackingService jobTrackingService;
    @Mock
    private ContentTxService contentTxService;
    @Mock
    private AdminQuery adminQuery;

    @InjectMocks
    private AdminService adminService;

    private AdminContentRegisterRequest registerRequest() {
        return new AdminContentRegisterRequest(
                "수정된 제목", "수정된 설명",
                "https://poster", "https://backdrop", "https://trailer",
                LocalDateTime.of(2025, 7, 11, 0, 0),
                120, 0, "전체 관람가",
                List.of(new AdminCategoryDTO("영화", List.of("액션"))),
                List.of("대한민국"),
                List.of(1L),
                List.of(1L),
                List.of(new AdminPlatformDTO("넷플릭스", "https://watch"))
        );
    }

    private AdminContentUpdateRequest updateRequest() {
        return new AdminContentUpdateRequest(
                "수정 제목", "수정 설명",
                "https://poster2", "https://backdrop2", "https://trailer2",
                LocalDateTime.of(2025, 7, 12, 0, 0),
                130, 1, "15세 관람가",
                List.of(new AdminCategoryDTO("영화", List.of("액션"))),
                List.of("미국"),
                List.of(1L),
                List.of(1L),
                List.of(new AdminPlatformDTO("왓챠", "https://watch2"))
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private StreamingJobSpec captureSpec() {
        ArgumentCaptor<StreamingJobSpec> captor = ArgumentCaptor.forClass(StreamingJobSpec.class);
        verify(streamingJobExecutor).execute(captor.capture());
        return captor.getValue();
    }

    @DisplayName("resubmitRegisterJob: INVALID 상태가 아니면 RestApiException + executor 미진입")
    @Test
    void resubmitRegisterJob_notInvalidStatus() {
        AdminContentRegisterJob job = mock(AdminContentRegisterJob.class);
        given(job.getStatus()).willReturn(StreamingStatus.COMPLETED);
        given(adminQuery.findAdminContentRegisterJobById(100L)).willReturn(job);

        assertThatThrownBy(() -> adminService.resubmitRegisterJob(100L, registerRequest()))
                .isInstanceOf(RestApiException.class);

        verify(streamingJobExecutor, never()).execute(any());
    }

    @DisplayName("resubmitRegisterJob: INVALID면 기존 jobId를 그대로 등록 협력자에 위임한다")
    @Test
    @SuppressWarnings("unchecked")
    void resubmitRegisterJob_delegatesWithJobId() {
        AdminContentRegisterJob job = mock(AdminContentRegisterJob.class);
        given(job.getStatus()).willReturn(StreamingStatus.INVALID);
        given(job.getAdminId()).willReturn(1L);
        given(adminQuery.findAdminContentRegisterJobById(100L)).willReturn(job);

        AdminContentRegisterRequest req = registerRequest();
        AdminContentRegisterResponse expected = new AdminContentRegisterResponse(100L);
        given(streamingJobExecutor.execute(any())).willReturn(expected);

        AdminContentRegisterResponse actual = adminService.resubmitRegisterJob(100L, req);
        assertThat(actual).isSameAs(expected);

        StreamingJobSpec<AdminContentRegisterResponse> spec = captureSpec();

        List<JobValidationError> errs = List.of();
        given(adminQuery.collectValidationErrors(any(), any(), any(), any())).willReturn(errs);
        assertThat(spec.validate()).isSameAs(errs);

        given(jobTrackingService.persistRegisterInvalid(eq(100L), eq(1L), eq(req), eq(errs)))
                .willReturn(100L);
        assertThat(spec.persistInvalid(errs)).isEqualTo(100L);

        given(contentTxService.processRegisterAndComplete(eq(100L), eq(1L), eq(req)))
                .willReturn(100L);
        assertThat(spec.processAndComplete().registerJobId()).isEqualTo(100L);
    }

    @DisplayName("resubmitUpdateJob: 기존 jobId/contentId를 그대로 수정 협력자에 위임한다")
    @Test
    @SuppressWarnings("unchecked")
    void resubmitUpdateJob_delegatesWithJobId() {
        AdminContentUpdateJob job = mock(AdminContentUpdateJob.class);
        given(job.getStatus()).willReturn(StreamingStatus.INVALID);
        given(job.getAdminId()).willReturn(1L);
        given(job.getContentId()).willReturn(5L);
        given(adminQuery.findAdminContentUpdateJobById(200L)).willReturn(job);

        AdminContentUpdateRequest req = updateRequest();
        AdminContentUpdateResponse expected = new AdminContentUpdateResponse(200L);
        given(streamingJobExecutor.execute(any())).willReturn(expected);

        AdminContentUpdateResponse actual = adminService.resubmitUpdateJob(200L, req);
        assertThat(actual).isSameAs(expected);

        StreamingJobSpec<AdminContentUpdateResponse> spec = captureSpec();

        given(adminQuery.collectContentIdValidationError(5L)).willReturn(List.of());
        given(adminQuery.collectValidationErrors(any(), any(), any(), any()))
                .willReturn(List.of());
        assertThat(spec.validate()).isEmpty();

        given(contentTxService.processUpdateAndComplete(eq(200L), eq(1L), eq(5L), eq(req)))
                .willReturn(200L);
        assertThat(spec.processAndComplete().updateJobId()).isEqualTo(200L);
    }

    @DisplayName("resubmitDeleteJob: 새 contentId와 기존 jobId를 삭제 협력자에 위임한다")
    @Test
    @SuppressWarnings("unchecked")
    void resubmitDeleteJob_delegatesWithJobId() {
        AdminContentDeleteJob job = mock(AdminContentDeleteJob.class);
        given(job.getStatus()).willReturn(StreamingStatus.INVALID);
        given(job.getAdminId()).willReturn(1L);
        given(adminQuery.findAdminContentDelJobById(300L)).willReturn(job);

        Long newContentId = 50L;
        AdminContentDeleteResponse expected = new AdminContentDeleteResponse(300L);
        given(streamingJobExecutor.execute(any())).willReturn(expected);

        AdminContentDeleteResponse actual = adminService.resubmitDeleteJob(300L, newContentId);
        assertThat(actual).isSameAs(expected);

        StreamingJobSpec<AdminContentDeleteResponse> spec = captureSpec();

        List<JobValidationError> errs = List.of();
        given(adminQuery.collectContentIdValidationError(newContentId)).willReturn(errs);
        assertThat(spec.validate()).isSameAs(errs);

        given(jobTrackingService.persistDeleteInvalid(eq(300L), eq(1L), eq(newContentId), eq(errs)))
                .willReturn(300L);
        assertThat(spec.persistInvalid(errs)).isEqualTo(300L);

        given(contentTxService.processDeleteAndComplete(eq(300L), eq(1L), eq(newContentId)))
                .willReturn(300L);
        assertThat(spec.processAndComplete().deleteJobId()).isEqualTo(300L);
    }
}
