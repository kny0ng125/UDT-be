package com.example.udtbe.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.example.udtbe.domain.admin.entity.Admin;
import com.example.udtbe.domain.admin.service.AdminQuery;
import com.example.udtbe.domain.admin.service.AdminService;
import com.example.udtbe.domain.batch.dto.JobValidationError;
import com.example.udtbe.domain.batch.entity.AdminContentDeleteJob;
import com.example.udtbe.domain.batch.entity.AdminContentRegisterJob;
import com.example.udtbe.domain.batch.entity.AdminContentUpdateJob;
import com.example.udtbe.domain.batch.entity.enums.BatchStatus;
import com.example.udtbe.domain.batch.repository.AdminContentDeleteJobRepository;
import com.example.udtbe.domain.batch.repository.AdminContentRegisterJobRepository;
import com.example.udtbe.domain.batch.repository.AdminContentUpdateJobRepository;
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
import java.util.Collections;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminServiceStreamingTest {

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

    @InjectMocks
    private AdminService adminService;

    private Admin admin;
    private AdminContentRegisterRequest registerRequest;
    private AdminContentUpdateRequest updateRequest;

    @BeforeEach
    void setUp() {
        admin = mock(Admin.class);
        given(admin.getId()).willReturn(1L);

        registerRequest = new AdminContentRegisterRequest(
                "테스트 제목", "테스트 설명",
                "https://poster", "https://backdrop", "https://trailer",
                LocalDateTime.of(2025, 7, 11, 0, 0),
                120, 0, "전체 관람가",
                List.of(new AdminCategoryDTO("영화", List.of("액션"))),
                List.of("대한민국"),
                List.of(1L),
                List.of(1L),
                List.of(new AdminPlatformDTO("넷플릭스", "https://watch"))
        );

        updateRequest = new AdminContentUpdateRequest(
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

    @DisplayName("registerBulkContent: 성공 시 Job이 COMPLETED 상태이며 REGISTER 이벤트를 발행한다")
    @Test
    void registerBulkContent_success() {
        // given
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

        ArgumentCaptor<AdminContentRegisterJob> jobCaptor =
                ArgumentCaptor.forClass(AdminContentRegisterJob.class);
        given(adminContentRegisterJobRepository.save(jobCaptor.capture()))
                .willAnswer(inv -> {
                    AdminContentRegisterJob j = inv.getArgument(0);
                    ReflectionTestUtils.setField(j, "id", 100L);
                    return j;
                });

        ArgumentCaptor<ContentStreamingEvent> eventCaptor =
                ArgumentCaptor.forClass(ContentStreamingEvent.class);

        // when
        AdminContentRegisterResponse response =
                adminService.registerBulkContent(admin, registerRequest);

        // then
        verify(adminQuery).collectValidationErrors(any(), any(), any(), any());
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        ContentStreamingEvent event = eventCaptor.getValue();
        assertThat(event.getType()).isEqualTo(ContentStreamingType.REGISTER);
        assertThat(event.getContentId()).isEqualTo(42L);
        assertThat(event.getMetadata()).isSameAs(metadata);

        AdminContentRegisterJob savedJob = jobCaptor.getValue();
        assertThat(savedJob.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(savedJob.getFinishedAt()).isNotNull();
        assertThat(response).isNotNull();
    }

    @DisplayName("registerBulkContent: 검증 실패 시 Job INVALID + 이벤트 미발행 + BulkValidationException")
    @Test
    void registerBulkContent_validationFailure() {
        // given
        JobValidationError error = new JobValidationError(
                "categories[0].categoryType", "BadType",
                "CATEGORY_TYPE_BAD_REQUEST", "올바르지 않은 분류 타입입니다.");
        given(adminQuery.collectValidationErrors(any(), any(), any(), any()))
                .willReturn(List.of(error));

        ArgumentCaptor<AdminContentRegisterJob> jobCaptor =
                ArgumentCaptor.forClass(AdminContentRegisterJob.class);
        given(adminContentRegisterJobRepository.save(jobCaptor.capture()))
                .willAnswer(inv -> {
                    AdminContentRegisterJob j = inv.getArgument(0);
                    ReflectionTestUtils.setField(j, "id", 100L);
                    return j;
                });

        // when & then
        assertThatThrownBy(() -> adminService.registerBulkContent(admin, registerRequest))
                .isInstanceOf(BulkValidationException.class)
                .extracting("errors")
                .asList()
                .hasSize(1);

        AdminContentRegisterJob savedJob = jobCaptor.getValue();
        assertThat(savedJob.getStatus()).isEqualTo(BatchStatus.INVALID);
        assertThat(savedJob.getErrorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(savedJob.getValidationErrors()).hasSize(1);
        assertThat(savedJob.getValidationErrors().get(0).field())
                .isEqualTo("categories[0].categoryType");
        assertThat(savedJob.getFinishedAt()).isNotNull();
        verify(eventPublisher, never()).publishEvent(any(ContentStreamingEvent.class));
    }

    @DisplayName("updateBulkContent: 성공 시 Job이 COMPLETED 상태이며 UPDATE 이벤트를 발행한다")
    @Test
    void updateBulkContent_success() {
        // given
        Long contentId = 7L;
        Content content = mock(Content.class);
        ContentMetadata metadata = mock(ContentMetadata.class);

        given(adminQuery.collectContentIdValidationError(contentId))
                .willReturn(Collections.emptyList());
        given(adminQuery.collectValidationErrors(any(), any(), any(), any()))
                .willReturn(Collections.emptyList());

        given(adminQuery.findContentByContentId(contentId)).willReturn(content);
        given(adminQuery.findContentMetadataByContentId(contentId)).willReturn(metadata);

        Category category = mock(Category.class);
        given(adminQuery.findByCategoryType(any(CategoryType.class))).willReturn(category);
        given(adminQuery.findByGenreTypeAndCategory(any(GenreType.class), any(Category.class)))
                .willReturn(mock(Genre.class));
        given(adminQuery.findCastByCastId(anyLong())).willReturn(mock(Cast.class));
        given(adminQuery.findDirectorByDirectorId(anyLong())).willReturn(mock(Director.class));
        given(adminQuery.findOrSaveCountry(anyString())).willReturn(mock(Country.class));
        given(adminQuery.findByPlatform(any(PlatformType.class))).willReturn(mock(Platform.class));

        ArgumentCaptor<AdminContentUpdateJob> jobCaptor =
                ArgumentCaptor.forClass(AdminContentUpdateJob.class);
        given(adminContentUpdateJobRepository.save(jobCaptor.capture()))
                .willAnswer(inv -> {
                    AdminContentUpdateJob j = inv.getArgument(0);
                    ReflectionTestUtils.setField(j, "id", 200L);
                    return j;
                });

        ArgumentCaptor<ContentStreamingEvent> eventCaptor =
                ArgumentCaptor.forClass(ContentStreamingEvent.class);

        // when
        AdminContentUpdateResponse response =
                adminService.updateBulkContent(admin, contentId, updateRequest);

        // then
        verify(adminQuery).collectContentIdValidationError(contentId);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        ContentStreamingEvent event = eventCaptor.getValue();
        assertThat(event.getType()).isEqualTo(ContentStreamingType.UPDATE);
        assertThat(event.getContentId()).isEqualTo(contentId);
        assertThat(event.getMetadata()).isSameAs(metadata);

        AdminContentUpdateJob savedJob = jobCaptor.getValue();
        assertThat(savedJob.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(savedJob.getFinishedAt()).isNotNull();
        assertThat(response).isNotNull();
    }

    @DisplayName("deleteBulkContent: 성공 시 Job이 COMPLETED 상태이며 DELETE 이벤트를 발행한다")
    @Test
    void deleteBulkContent_success() {
        // given
        Long contentId = 9L;
        Content content = mock(Content.class);
        ContentMetadata metadata = mock(ContentMetadata.class);
        given(adminQuery.collectContentIdValidationError(contentId))
                .willReturn(Collections.emptyList());
        given(adminQuery.findAndValidContentByContentId(contentId)).willReturn(content);
        given(adminQuery.findContentMetadataByContentId(contentId)).willReturn(metadata);

        ArgumentCaptor<AdminContentDeleteJob> jobCaptor =
                ArgumentCaptor.forClass(AdminContentDeleteJob.class);
        given(adminContentDeleteJobRepository.save(jobCaptor.capture()))
                .willAnswer(inv -> {
                    AdminContentDeleteJob j = inv.getArgument(0);
                    ReflectionTestUtils.setField(j, "id", 300L);
                    return j;
                });

        ArgumentCaptor<ContentStreamingEvent> eventCaptor =
                ArgumentCaptor.forClass(ContentStreamingEvent.class);

        // when
        AdminContentDeleteResponse response =
                adminService.deleteBulkContent(admin, contentId);

        // then
        verify(content).delete(eq(true));
        verify(metadata).delete(eq(true));
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        ContentStreamingEvent event = eventCaptor.getValue();
        assertThat(event.getType()).isEqualTo(ContentStreamingType.DELETE);
        assertThat(event.getContentId()).isEqualTo(contentId);
        assertThat(event.getMetadata()).isNull();

        AdminContentDeleteJob savedJob = jobCaptor.getValue();
        assertThat(savedJob.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(savedJob.getFinishedAt()).isNotNull();
        assertThat(response).isNotNull();
    }

    @DisplayName("deleteBulkContent: 컨텐츠 미존재 시 Job INVALID + 이벤트 미발행")
    @Test
    void deleteBulkContent_contentNotFound() {
        // given
        Long contentId = 999L;
        JobValidationError error = new JobValidationError(
                "contentId", "999", "CONTENT_NOT_FOUND", "콘텐츠를 찾을 수 없습니다.");
        given(adminQuery.collectContentIdValidationError(contentId))
                .willReturn(List.of(error));

        ArgumentCaptor<AdminContentDeleteJob> jobCaptor =
                ArgumentCaptor.forClass(AdminContentDeleteJob.class);
        given(adminContentDeleteJobRepository.save(jobCaptor.capture()))
                .willAnswer(inv -> {
                    AdminContentDeleteJob j = inv.getArgument(0);
                    ReflectionTestUtils.setField(j, "id", 300L);
                    return j;
                });

        // when & then
        assertThatThrownBy(() -> adminService.deleteBulkContent(admin, contentId))
                .isInstanceOf(BulkValidationException.class);

        AdminContentDeleteJob savedJob = jobCaptor.getValue();
        assertThat(savedJob.getStatus()).isEqualTo(BatchStatus.INVALID);
        assertThat(savedJob.getErrorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(savedJob.getValidationErrors()).hasSize(1);
        assertThat(savedJob.getValidationErrors().get(0).field()).isEqualTo("contentId");
        verify(eventPublisher, never()).publishEvent(any(ContentStreamingEvent.class));
    }
}
