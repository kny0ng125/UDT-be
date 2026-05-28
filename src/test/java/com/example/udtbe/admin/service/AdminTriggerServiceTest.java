package com.example.udtbe.admin.service;

import static com.example.udtbe.domain.admin.service.AdminTriggerService.MAX_RETRY_COUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.udtbe.common.fixture.AdminContentDeleteJobFixture;
import com.example.udtbe.common.fixture.AdminContentRegisterJobFixture;
import com.example.udtbe.domain.admin.dto.request.AdminContentRegisterRequest;
import com.example.udtbe.domain.admin.service.AdminQuery;
import com.example.udtbe.domain.admin.service.AdminService;
import com.example.udtbe.domain.admin.service.AdminTriggerService;
import com.example.udtbe.domain.streaming.dto.JobValidationError;
import com.example.udtbe.domain.streaming.entity.AdminContentDeleteJob;
import com.example.udtbe.domain.streaming.entity.AdminContentRegisterJob;
import com.example.udtbe.domain.streaming.entity.enums.StreamingStatus;
import com.example.udtbe.domain.streaming.repository.AdminContentDeleteJobRepository;
import com.example.udtbe.domain.streaming.repository.AdminContentRegisterJobRepository;
import com.example.udtbe.domain.streaming.repository.AdminContentUpdateJobRepository;
import com.example.udtbe.domain.content.entity.Content;
import com.example.udtbe.domain.content.entity.ContentMetadata;
import com.example.udtbe.domain.content.event.ContentStreamingEvent;
import com.example.udtbe.domain.content.event.ContentStreamingType;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class AdminTriggerServiceTest {

    @Mock
    private AdminContentRegisterJobRepository registerJobRepository;
    @Mock
    private AdminContentUpdateJobRepository updateJobRepository;
    @Mock
    private AdminContentDeleteJobRepository deleteJobRepository;
    @Mock
    private AdminService adminService;
    @Mock
    private AdminQuery adminQuery;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AdminTriggerService adminTriggerService;

    @DisplayName("retryFailedBatch: 실패한 등록 Job을 재처리해 COMPLETED + REGISTER 이벤트 발행")
    @Test
    void retry_register_success() {
        // given
        AdminContentRegisterJob job = AdminContentRegisterJobFixture
                .createPendingJob(1L, "재시도제목", "재시도설명");
        job.changeStatus(StreamingStatus.FAILED);

        Content content = mock(Content.class);
        given(content.getId()).willReturn(42L);
        ContentMetadata metadata = mock(ContentMetadata.class);

        given(registerJobRepository.findByStatusAndRetryCountLessThan(
                StreamingStatus.FAILED, MAX_RETRY_COUNT)).willReturn(List.of(job));
        given(updateJobRepository.findByStatusAndRetryCountLessThan(
                StreamingStatus.FAILED, MAX_RETRY_COUNT)).willReturn(List.of());
        given(deleteJobRepository.findByStatusAndRetryCountLessThan(
                StreamingStatus.FAILED, MAX_RETRY_COUNT)).willReturn(List.of());
        given(adminQuery.collectValidationErrors(any(), any(), any(), any()))
                .willReturn(Collections.emptyList());
        given(adminService.registerContent(any(AdminContentRegisterRequest.class)))
                .willReturn(content);
        given(adminQuery.findContentMetadataByContentId(42L)).willReturn(metadata);

        ArgumentCaptor<ContentStreamingEvent> eventCaptor =
                ArgumentCaptor.forClass(ContentStreamingEvent.class);

        // when
        adminTriggerService.retryFailedBatch();

        // then
        verify(adminService).registerContent(any(AdminContentRegisterRequest.class));
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getType()).isEqualTo(ContentStreamingType.REGISTER);
        assertThat(eventCaptor.getValue().getContentId()).isEqualTo(42L);
        assertThat(job.getStatus()).isEqualTo(StreamingStatus.COMPLETED);
        assertThat(job.getRetryCount()).isEqualTo(1);
        assertThat(job.getFinishedAt()).isNotNull();
    }

    @DisplayName("retryFailedBatch: 재시도 시 검증 실패하면 Job INVALID + validationErrors 설정")
    @Test
    void retry_register_validationFailure() {
        // given
        AdminContentRegisterJob job = AdminContentRegisterJobFixture
                .createPendingJob(1L, "제목", "설명");
        job.changeStatus(StreamingStatus.FAILED);

        given(registerJobRepository.findByStatusAndRetryCountLessThan(
                StreamingStatus.FAILED, MAX_RETRY_COUNT)).willReturn(List.of(job));
        given(updateJobRepository.findByStatusAndRetryCountLessThan(
                StreamingStatus.FAILED, MAX_RETRY_COUNT)).willReturn(List.of());
        given(deleteJobRepository.findByStatusAndRetryCountLessThan(
                StreamingStatus.FAILED, MAX_RETRY_COUNT)).willReturn(List.of());

        JobValidationError error = new JobValidationError(
                "casts[0]", "1", "CAST_NOT_FOUND", "출연진을 찾을 수 없습니다.");
        given(adminQuery.collectValidationErrors(any(), any(), any(), any()))
                .willReturn(List.of(error));

        // when
        adminTriggerService.retryFailedBatch();

        // then
        assertThat(job.getStatus()).isEqualTo(StreamingStatus.INVALID);
        assertThat(job.getErrorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(job.getValidationErrors()).hasSize(1);
        assertThat(job.getRetryCount()).isEqualTo(1);
        verify(adminService, never()).registerContent(any());
        verify(eventPublisher, never()).publishEvent(any(ContentStreamingEvent.class));
    }

    @DisplayName("retryFailedBatch: 등록 재시도 중 일반 예외 발생 시 Job이 FAILED로 마킹되고 retryCount 증가")
    @Test
    void retry_register_processingFailure() {
        // given
        AdminContentRegisterJob job = AdminContentRegisterJobFixture
                .createPendingJob(1L, "제목", "설명");
        job.changeStatus(StreamingStatus.FAILED);

        given(registerJobRepository.findByStatusAndRetryCountLessThan(
                StreamingStatus.FAILED, MAX_RETRY_COUNT)).willReturn(List.of(job));
        given(updateJobRepository.findByStatusAndRetryCountLessThan(
                StreamingStatus.FAILED, MAX_RETRY_COUNT)).willReturn(List.of());
        given(deleteJobRepository.findByStatusAndRetryCountLessThan(
                StreamingStatus.FAILED, MAX_RETRY_COUNT)).willReturn(List.of());
        given(adminQuery.collectValidationErrors(any(), any(), any(), any()))
                .willReturn(Collections.emptyList());
        given(adminService.registerContent(any(AdminContentRegisterRequest.class)))
                .willThrow(new RuntimeException("DB connection lost"));

        // when
        adminTriggerService.retryFailedBatch();

        // then
        assertThat(job.getStatus()).isEqualTo(StreamingStatus.FAILED);
        assertThat(job.getErrorCode()).isEqualTo("RETRY_FAILED");
        assertThat(job.getRetryCount()).isEqualTo(1);
        verify(eventPublisher, never()).publishEvent(any(ContentStreamingEvent.class));
    }

    @DisplayName("retryFailedBatch: 실패한 삭제 Job을 재처리해 COMPLETED + DELETE 이벤트 발행")
    @Test
    void retry_delete_success() {
        // given
        AdminContentDeleteJob job = AdminContentDeleteJobFixture.createPendingJob(1L, 7L);
        job.changeStatus(StreamingStatus.FAILED);

        given(registerJobRepository.findByStatusAndRetryCountLessThan(
                StreamingStatus.FAILED, MAX_RETRY_COUNT)).willReturn(List.of());
        given(updateJobRepository.findByStatusAndRetryCountLessThan(
                StreamingStatus.FAILED, MAX_RETRY_COUNT)).willReturn(List.of());
        given(deleteJobRepository.findByStatusAndRetryCountLessThan(
                StreamingStatus.FAILED, MAX_RETRY_COUNT)).willReturn(List.of(job));
        given(adminQuery.collectContentIdValidationError(7L))
                .willReturn(Collections.emptyList());

        ArgumentCaptor<ContentStreamingEvent> eventCaptor =
                ArgumentCaptor.forClass(ContentStreamingEvent.class);

        // when
        adminTriggerService.retryFailedBatch();

        // then
        verify(adminService).deleteContent(7L);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getType()).isEqualTo(ContentStreamingType.DELETE);
        assertThat(eventCaptor.getValue().getContentId()).isEqualTo(7L);
        assertThat(eventCaptor.getValue().getMetadata()).isNull();
        assertThat(job.getStatus()).isEqualTo(StreamingStatus.COMPLETED);
        assertThat(job.getRetryCount()).isEqualTo(1);
    }

    @DisplayName("retryFailedBatch: FAILED Job이 없으면 어떤 동작도 일어나지 않는다")
    @Test
    void retry_emptyFailedLists() {
        // given
        given(registerJobRepository.findByStatusAndRetryCountLessThan(
                StreamingStatus.FAILED, MAX_RETRY_COUNT)).willReturn(List.of());
        given(updateJobRepository.findByStatusAndRetryCountLessThan(
                StreamingStatus.FAILED, MAX_RETRY_COUNT)).willReturn(List.of());
        given(deleteJobRepository.findByStatusAndRetryCountLessThan(
                StreamingStatus.FAILED, MAX_RETRY_COUNT)).willReturn(List.of());

        // when
        adminTriggerService.retryFailedBatch();

        // then
        verify(adminService, never()).registerContent(any());
        verify(adminService, never()).updateContent(anyLong(), any());
        verify(adminService, never()).deleteContent(anyLong());
        verify(eventPublisher, never()).publishEvent(any(ContentStreamingEvent.class));
    }

    @DisplayName("retryFailedBatch: 재시도 횟수가 MAX에 도달한 Job은 조회 대상에서 제외된다")
    @Test
    void retry_excludesExhaustedJobs() {
        // given - findByStatusAndRetryCountLessThan(FAILED, 3)이 빈 리스트를 반환
        // (실제 DB에서는 retryCount=3인 Job이 있어도 제외됨)
        given(registerJobRepository.findByStatusAndRetryCountLessThan(
                StreamingStatus.FAILED, MAX_RETRY_COUNT)).willReturn(List.of());
        given(updateJobRepository.findByStatusAndRetryCountLessThan(
                StreamingStatus.FAILED, MAX_RETRY_COUNT)).willReturn(List.of());
        given(deleteJobRepository.findByStatusAndRetryCountLessThan(
                StreamingStatus.FAILED, MAX_RETRY_COUNT)).willReturn(List.of());

        // when
        adminTriggerService.retryFailedBatch();

        // then
        verify(registerJobRepository).findByStatusAndRetryCountLessThan(
                StreamingStatus.FAILED, MAX_RETRY_COUNT);
        verify(updateJobRepository).findByStatusAndRetryCountLessThan(
                StreamingStatus.FAILED, MAX_RETRY_COUNT);
        verify(deleteJobRepository).findByStatusAndRetryCountLessThan(
                StreamingStatus.FAILED, MAX_RETRY_COUNT);
    }
}
