package com.example.udtbe.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
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
import com.example.udtbe.domain.admin.service.ContentTxService;
import com.example.udtbe.domain.admin.service.JobTrackingService;
import com.example.udtbe.domain.admin.service.StreamingJobExecutor;
import com.example.udtbe.domain.admin.service.StreamingJobSpec;
import com.example.udtbe.domain.batch.dto.JobValidationError;
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

/**
 * 스트리밍 전환(P0) 이후 단위 검증.
 *
 * <p>실제 트랜잭션 격리(INVALID/FAILED 행이 롤백돼도 실존)는 통합테스트(B, 추후)에서 검증한다.
 * 여기서는 (1) AdminService가 executor에 위임하는지, (2) 조립된 spec이 타입별로 올바른
 * 협력자({@link JobTrackingService}/{@link ContentTxService})에 위임하는지만 단위로 확인한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceStreamingTest {

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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private StreamingJobSpec captureSpec() {
        ArgumentCaptor<StreamingJobSpec> captor = ArgumentCaptor.forClass(StreamingJobSpec.class);
        verify(streamingJobExecutor).execute(captor.capture());
        return captor.getValue();
    }

    @DisplayName("registerBulkContent: executor에 위임하고, spec은 신규(jobId=null) 등록 협력자로 위임한다")
    @Test
    @SuppressWarnings("unchecked")
    void registerBulkContent_delegatesAndWiresSpec() {
        AdminContentRegisterResponse expected = new AdminContentRegisterResponse(100L);
        given(streamingJobExecutor.execute(any())).willReturn(expected);

        AdminContentRegisterResponse actual =
                adminService.registerBulkContent(admin, registerRequest);

        assertThat(actual).isSameAs(expected);

        StreamingJobSpec<AdminContentRegisterResponse> spec = captureSpec();

        List<JobValidationError> errs = List.of();
        given(adminQuery.collectValidationErrors(any(), any(), any(), any())).willReturn(errs);
        assertThat(spec.validate()).isSameAs(errs);

        given(jobTrackingService.persistRegisterInvalid(
                isNull(), eq(1L), eq(registerRequest), eq(errs))).willReturn(7L);
        assertThat(spec.persistInvalid(errs)).isEqualTo(7L);

        given(contentTxService.processRegisterAndComplete(
                isNull(), eq(1L), eq(registerRequest))).willReturn(9L);
        assertThat(spec.processAndComplete().registerJobId()).isEqualTo(9L);

        RuntimeException ex = new RuntimeException("boom");
        given(jobTrackingService.persistRegisterFailed(
                isNull(), eq(1L), eq(registerRequest), eq(ex))).willReturn(8L);
        assertThat(spec.persistFailed(ex)).isEqualTo(8L);
    }

    @DisplayName("updateBulkContent: spec.validate는 contentId 검증 + 일반 검증을 합치고, 협력자로 위임한다")
    @Test
    @SuppressWarnings("unchecked")
    void updateBulkContent_delegatesAndWiresSpec() {
        Long contentId = 7L;
        AdminContentUpdateResponse expected = new AdminContentUpdateResponse(200L);
        given(streamingJobExecutor.execute(any())).willReturn(expected);

        AdminContentUpdateResponse actual =
                adminService.updateBulkContent(admin, contentId, updateRequest);

        assertThat(actual).isSameAs(expected);

        StreamingJobSpec<AdminContentUpdateResponse> spec = captureSpec();

        JobValidationError cidErr = new JobValidationError(
                "contentId", "7", "CONTENT_NOT_FOUND", "콘텐츠를 찾을 수 없습니다.");
        given(adminQuery.collectContentIdValidationError(contentId)).willReturn(List.of(cidErr));
        given(adminQuery.collectValidationErrors(any(), any(), any(), any()))
                .willReturn(List.of());
        assertThat(spec.validate()).containsExactly(cidErr);

        given(jobTrackingService.persistUpdateInvalid(
                isNull(), eq(1L), eq(contentId), eq(updateRequest), any())).willReturn(21L);
        assertThat(spec.persistInvalid(List.of(cidErr))).isEqualTo(21L);

        given(contentTxService.processUpdateAndComplete(
                isNull(), eq(1L), eq(contentId), eq(updateRequest))).willReturn(22L);
        assertThat(spec.processAndComplete().updateJobId()).isEqualTo(22L);
    }

    @DisplayName("deleteBulkContent: spec.validate는 contentId 검증만, 협력자로 위임한다")
    @Test
    @SuppressWarnings("unchecked")
    void deleteBulkContent_delegatesAndWiresSpec() {
        Long contentId = 9L;
        AdminContentDeleteResponse expected = new AdminContentDeleteResponse(300L);
        given(streamingJobExecutor.execute(any())).willReturn(expected);

        AdminContentDeleteResponse actual = adminService.deleteBulkContent(admin, contentId);

        assertThat(actual).isSameAs(expected);

        StreamingJobSpec<AdminContentDeleteResponse> spec = captureSpec();

        List<JobValidationError> errs = List.of();
        given(adminQuery.collectContentIdValidationError(contentId)).willReturn(errs);
        assertThat(spec.validate()).isSameAs(errs);

        given(jobTrackingService.persistDeleteInvalid(isNull(), eq(1L), eq(contentId), eq(errs)))
                .willReturn(31L);
        assertThat(spec.persistInvalid(errs)).isEqualTo(31L);

        given(contentTxService.processDeleteAndComplete(isNull(), eq(1L), eq(contentId)))
                .willReturn(32L);
        assertThat(spec.processAndComplete().deleteJobId()).isEqualTo(32L);
    }
}
