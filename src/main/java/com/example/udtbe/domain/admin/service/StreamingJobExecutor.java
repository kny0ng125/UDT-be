package com.example.udtbe.domain.admin.service;

import com.example.udtbe.domain.batch.dto.JobValidationError;
import com.example.udtbe.global.exception.BulkValidationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 스트리밍 작업 공통 오케스트레이터.
 *
 * <p>이 클래스에는 절대 {@code @Transactional}을 붙이지 않는다. 트랜잭션이 없어야
 * {@link StreamingJobSpec#persistInvalid}/{@link StreamingJobSpec#persistFailed}의
 * REQUIRES_NEW와 {@link StreamingJobSpec#processAndComplete}의 콘텐츠 트랜잭션이
 * 서로 독립적으로 커밋/롤백된다.</p>
 */
@Component
@Slf4j
public class StreamingJobExecutor {

    public <R> R execute(StreamingJobSpec<R> spec) {
        List<JobValidationError> errors = spec.validate();
        if (!errors.isEmpty()) {
            Long jobId = spec.persistInvalid(errors);
            throw new BulkValidationException(jobId, errors);
        }

        try {
            return spec.processAndComplete();
        } catch (BulkValidationException e) {
            throw e;
        } catch (Exception e) {
            Long jobId = spec.persistFailed(e);
            log.warn("스트리밍 작업 처리 실패 - jobId={}: {}", jobId, e.getMessage());
            throw e;
        }
    }
}
