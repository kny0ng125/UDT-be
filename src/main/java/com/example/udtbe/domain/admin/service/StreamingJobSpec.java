package com.example.udtbe.domain.admin.service;

import com.example.udtbe.domain.streaming.dto.JobValidationError;
import java.util.List;

/**
 * 스트리밍 작업(등록/수정/삭제, 신규/재제출) 한 건의 분기 로직을 담는 명세.
 *
 * <p>구현체는 콘텐츠를 변경하지 않는 검증({@link #validate()})과,
 * 트랜잭션 경계가 분리된 3가지 영속화 동작만 조립한다. 분기 오케스트레이션은
 * {@link StreamingJobExecutor}가 담당하며, 트랜잭션은 각 영속화 메서드 내부에서만 시작된다.</p>
 *
 * @param <R> API 응답 타입
 */
public interface StreamingJobSpec<R> {

    /** 콘텐츠를 변경하지 않는 읽기 전용 검증. 오류가 없으면 빈 리스트. */
    List<JobValidationError> validate();

    /** 검증 실패 시 INVALID Job을 독립 트랜잭션(REQUIRES_NEW)으로 저장하고 jobId 반환. */
    Long persistInvalid(List<JobValidationError> errors);

    /** 콘텐츠 처리 + Job COMPLETED + 이벤트 발행을 단일 트랜잭션으로 원자 처리. */
    R processAndComplete();

    /** 처리 실패 시 FAILED Job을 독립 트랜잭션(REQUIRES_NEW)으로 보존하고 jobId 반환. */
    Long persistFailed(Exception e);
}
