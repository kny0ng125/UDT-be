package com.example.udtbe.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.udtbe.domain.admin.service.StreamingJobExecutor;
import com.example.udtbe.domain.admin.service.StreamingJobSpec;
import com.example.udtbe.domain.streaming.dto.JobValidationError;
import com.example.udtbe.global.exception.BulkValidationException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** StreamingJobExecutor 분기 로직 단위 검증 (트랜잭션 격리는 통합테스트 B에서 별도 검증). */
@ExtendWith(MockitoExtension.class)
class StreamingJobExecutorTest {

    private final StreamingJobExecutor executor = new StreamingJobExecutor();

    @Mock
    private StreamingJobSpec<String> spec;

    @DisplayName("검증 통과: processAndComplete 결과를 반환하고 INVALID/FAILED 영속화는 호출되지 않는다")
    @Test
    void success_returnsProcessResult() {
        given(spec.validate()).willReturn(List.of());
        given(spec.processAndComplete()).willReturn("ok");

        String result = executor.execute(spec);

        assertThat(result).isEqualTo("ok");
        verify(spec, never()).persistInvalid(org.mockito.ArgumentMatchers.any());
        verify(spec, never()).persistFailed(org.mockito.ArgumentMatchers.any());
    }

    @DisplayName("검증 실패: persistInvalid가 커밋한 jobId로 BulkValidationException을 던지고 처리하지 않는다")
    @Test
    void validationFailure_persistsInvalidAndThrows() {
        JobValidationError error = new JobValidationError(
                "categories[0].categoryType", "BadType",
                "CATEGORY_TYPE_BAD_REQUEST", "올바르지 않은 분류 타입입니다.");
        given(spec.validate()).willReturn(List.of(error));
        given(spec.persistInvalid(List.of(error))).willReturn(42L);

        assertThatThrownBy(() -> executor.execute(spec))
                .isInstanceOf(BulkValidationException.class)
                .satisfies(e -> {
                    BulkValidationException bve = (BulkValidationException) e;
                    assertThat(bve.getJobId()).isEqualTo(42L);
                    assertThat(bve.getErrors()).containsExactly(error);
                });

        verify(spec, never()).processAndComplete();
        verify(spec, never()).persistFailed(org.mockito.ArgumentMatchers.any());
    }

    @DisplayName("처리 중 예외: persistFailed를 호출하고 원래 예외를 다시 던진다")
    @Test
    void processingFailure_persistsFailedAndRethrows() {
        RuntimeException boom = new RuntimeException("boom");
        given(spec.validate()).willReturn(List.of());
        given(spec.processAndComplete()).willThrow(boom);
        given(spec.persistFailed(boom)).willReturn(9L);

        assertThatThrownBy(() -> executor.execute(spec)).isSameAs(boom);

        verify(spec).persistFailed(boom);
    }

    @DisplayName("처리 중 BulkValidationException: 그대로 전파하고 persistFailed는 호출하지 않는다")
    @Test
    void processingThrowsBulkValidation_passesThrough() {
        BulkValidationException bve =
                new BulkValidationException(5L, List.of());
        given(spec.validate()).willReturn(List.of());
        given(spec.processAndComplete()).willThrow(bve);

        assertThatThrownBy(() -> executor.execute(spec)).isSameAs(bve);

        verify(spec, never()).persistFailed(org.mockito.ArgumentMatchers.any());
    }
}
