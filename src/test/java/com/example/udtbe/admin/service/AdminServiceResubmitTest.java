package com.example.udtbe.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.udtbe.domain.admin.dto.common.AdminCategoryDTO;
import com.example.udtbe.domain.admin.dto.common.AdminPlatformDTO;
import com.example.udtbe.domain.admin.dto.request.AdminContentRegisterRequest;
import com.example.udtbe.domain.admin.dto.response.AdminContentDeleteResponse;
import com.example.udtbe.domain.admin.dto.response.AdminContentRegisterResponse;
import com.example.udtbe.domain.admin.service.AdminQuery;
import com.example.udtbe.domain.admin.service.AdminService;
import com.example.udtbe.domain.batch.dto.JobValidationError;
import com.example.udtbe.domain.batch.entity.AdminContentDeleteJob;
import com.example.udtbe.domain.batch.entity.AdminContentRegisterJob;
import com.example.udtbe.domain.batch.entity.enums.BatchStatus;
import com.example.udtbe.domain.batch.repository.AdminContentDeleteJobRepository;
import com.example.udtbe.domain.batch.repository.AdminContentRegisterJobRepository;
import com.example.udtbe.domain.batch.repository.AdminContentUpdateJobRepository;
import com.example.udtbe.domain.batch.repository.BatchJobMetricRepository;
import com.example.udtbe.domain.content.entity.Cast;
import com.example.udtbe.domain.content.entity.Category;
import com.example.udtbe.domain.content.entity.Content;
import com.example.udtbe.domain.content.entity.ContentMetadata;
import com.example.udtbe.domain.content.entity.Country;
import com.example.udtbe.domain.content.entity.Director;
import com.example.udtbe.domain.content.entity.Genre;
import com.example.udtbe.domain.content.entity.Platform;
import com.example.udtbe.domain.content.entity.enums.CategoryType;
import com.example.udtbe.domain.content.entity.enums.GenreType;
import com.example.udtbe.domain.content.entity.enums.PlatformType;
import com.example.udtbe.domain.content.event.ContentStreamingEvent;
import com.example.udtbe.domain.content.event.ContentStreamingType;
import com.example.udtbe.domain.content.repository.ContentCastRepository;
import com.example.udtbe.domain.content.repository.ContentCategoryRepository;
import com.example.udtbe.domain.content.repository.ContentCountryRepository;
import com.example.udtbe.domain.content.repository.ContentDirectorRepository;
import com.example.udtbe.domain.content.repository.ContentGenreRepository;
import com.example.udtbe.domain.content.repository.ContentMetadataRepository;
import com.example.udtbe.domain.content.repository.ContentPlatformRepository;
import com.example.udtbe.domain.content.repository.ContentRepository;
import com.example.udtbe.global.exception.BulkValidationException;
import com.example.udtbe.global.exception.RestApiException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class AdminServiceResubmitTest {

    @Mock
    private AdminContentRegisterJobRepository adminContentRegisterJobRepository;
    @Mock
    private AdminContentUpdateJobRepository adminContentUpdateJobRepository;
    @Mock
    private AdminContentDeleteJobRepository adminContentDeleteJobRepository;
    @Mock
    private ContentMetadataRepository contentMetadataRepository;
    @Mock
    private ContentRepository contentRepository;
    @Mock
    private AdminQuery adminQuery;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ContentGenreRepository contentGenreRepository;
    @Mock
    private ContentCategoryRepository contentCategoryRepository;
    @Mock
    private ContentCastRepository contentCastRepository;
    @Mock
    private ContentCountryRepository contentCountryRepository;
    @Mock
    private ContentPlatformRepository contentPlatformRepository;
    @Mock
    private ContentDirectorRepository contentDirectorRepository;
    @Mock
    private BatchJobMetricRepository batchJobMetricRepository;

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

    private AdminContentRegisterJob invalidRegisterJob() {
        AdminContentRegisterJob job = mock(AdminContentRegisterJob.class);
        given(job.getId()).willReturn(100L);
        given(job.getStatus()).willReturn(BatchStatus.INVALID);
        return job;
    }

    @DisplayName("resubmitRegisterJob: INVALID Job을 수정된 데이터로 재처리하면 COMPLETED + 이벤트 발행")
    @Test
    void resubmitRegisterJob_success() {
        // given
        AdminContentRegisterJob job = invalidRegisterJob();
        given(adminQuery.findAdminContentRegisterJobById(100L)).willReturn(job);

        given(adminQuery.collectValidationErrors(any(), any(), any(), any()))
                .willReturn(Collections.emptyList());

        Content saved = mock(Content.class);
        given(saved.getId()).willReturn(42L);
        given(contentRepository.save(any(Content.class))).willReturn(saved);

        Category category = mock(Category.class);
        given(adminQuery.findByCategoryType(any(CategoryType.class))).willReturn(category);
        given(adminQuery.findByGenreTypeAndCategory(any(GenreType.class), any(Category.class)))
                .willReturn(mock(Genre.class));
        given(adminQuery.findCastByCastId(anyLong())).willReturn(mock(Cast.class));
        given(adminQuery.findDirectorByDirectorId(anyLong())).willReturn(mock(Director.class));
        given(adminQuery.findOrSaveCountry(anyString())).willReturn(mock(Country.class));
        given(adminQuery.findByPlatform(any(PlatformType.class))).willReturn(mock(Platform.class));
        given(contentMetadataRepository.save(any(ContentMetadata.class)))
                .willAnswer(inv -> inv.getArgument(0));

        ContentMetadata metadata = mock(ContentMetadata.class);
        given(adminQuery.findContentMetadataByContentId(42L)).willReturn(metadata);

        // when
        AdminContentRegisterResponse response =
                adminService.resubmitRegisterJob(100L, registerRequest());

        // then
        verify(job).clearErrors();
        verify(job).resetRetryCount();
        verify(job).updateFields(any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), any(), any(), any(), any(), any(), any());
        verify(job).changeStatus(BatchStatus.PROCESSING);
        verify(job).changeStatus(BatchStatus.COMPLETED);
        verify(eventPublisher).publishEvent(any(ContentStreamingEvent.class));
        assertThat(response).isNotNull();
    }

    @DisplayName("resubmitRegisterJob: INVALID 상태가 아니면 RestApiException")
    @Test
    void resubmitRegisterJob_notInvalidStatus() {
        // given
        AdminContentRegisterJob job = mock(AdminContentRegisterJob.class);
        given(job.getStatus()).willReturn(BatchStatus.COMPLETED);
        given(adminQuery.findAdminContentRegisterJobById(100L)).willReturn(job);

        // when & then
        assertThatThrownBy(() -> adminService.resubmitRegisterJob(100L, registerRequest()))
                .isInstanceOf(RestApiException.class);

        verify(eventPublisher, never()).publishEvent(any(ContentStreamingEvent.class));
    }

    @DisplayName("resubmitRegisterJob: 재제출 후에도 검증 실패하면 다시 INVALID로 마킹 + BulkValidationException")
    @Test
    void resubmitRegisterJob_validationFailsAgain() {
        // given
        AdminContentRegisterJob job = invalidRegisterJob();
        given(adminQuery.findAdminContentRegisterJobById(100L)).willReturn(job);

        JobValidationError error = new JobValidationError(
                "platforms[0].platformType", "BadPlatform",
                "PLATFORM_TYPE_BAD_REQUEST", "올바르지 않은 플랫폼 타입입니다.");
        given(adminQuery.collectValidationErrors(any(), any(), any(), any()))
                .willReturn(List.of(error));

        // when & then
        assertThatThrownBy(() -> adminService.resubmitRegisterJob(100L, registerRequest()))
                .isInstanceOf(BulkValidationException.class)
                .extracting("errors")
                .asList()
                .hasSize(1);

        verify(job).setValidationErrors(List.of(error));
        verify(job).changeStatus(BatchStatus.INVALID);
        verify(eventPublisher, never()).publishEvent(any(ContentStreamingEvent.class));
    }

    @DisplayName("resubmitDeleteJob: 새 contentId로 INVALID Job 재처리 시 COMPLETED + 이벤트 발행")
    @Test
    void resubmitDeleteJob_success() {
        // given
        AdminContentDeleteJob job = mock(AdminContentDeleteJob.class);
        given(job.getId()).willReturn(300L);
        given(job.getStatus()).willReturn(BatchStatus.INVALID);
        given(adminQuery.findAdminContentDelJobById(300L)).willReturn(job);

        Long newContentId = 50L;
        Content content = mock(Content.class);
        ContentMetadata metadata = mock(ContentMetadata.class);
        given(adminQuery.collectContentIdValidationError(newContentId))
                .willReturn(Collections.emptyList());
        given(adminQuery.findAndValidContentByContentId(newContentId)).willReturn(content);
        given(adminQuery.findContentMetadataByContentId(newContentId)).willReturn(metadata);

        // when
        AdminContentDeleteResponse response =
                adminService.resubmitDeleteJob(300L, newContentId);

        // then
        verify(job).updateContentId(newContentId);
        verify(job).clearErrors();
        verify(job).resetRetryCount();
        verify(job).changeStatus(BatchStatus.COMPLETED);
        verify(content).delete(true);
        verify(eventPublisher).publishEvent(any(ContentStreamingEvent.class));
        assertThat(response).isNotNull();
    }

}
