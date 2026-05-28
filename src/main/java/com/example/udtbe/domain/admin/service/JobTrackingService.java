package com.example.udtbe.domain.admin.service;

import com.example.udtbe.domain.admin.dto.AdminContentMapper;
import com.example.udtbe.domain.admin.dto.common.AdminCategoryDTO;
import com.example.udtbe.domain.admin.dto.common.AdminPlatformDTO;
import com.example.udtbe.domain.admin.dto.request.AdminContentRegisterRequest;
import com.example.udtbe.domain.admin.dto.request.AdminContentUpdateRequest;
import com.example.udtbe.domain.streaming.dto.JobValidationError;
import com.example.udtbe.domain.streaming.entity.AdminContentDeleteJob;
import com.example.udtbe.domain.streaming.entity.AdminContentRegisterJob;
import com.example.udtbe.domain.streaming.entity.AdminContentUpdateJob;
import com.example.udtbe.domain.streaming.entity.enums.StreamingStatus;
import com.example.udtbe.domain.streaming.repository.AdminContentDeleteJobRepository;
import com.example.udtbe.domain.streaming.repository.AdminContentRegisterJobRepository;
import com.example.udtbe.domain.streaming.repository.AdminContentUpdateJobRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * INVALID / FAILED Job 추적 기록을 <b>독립 트랜잭션(REQUIRES_NEW)</b>으로 보존한다.
 *
 * <p>콘텐츠 트랜잭션이 롤백되어도 여기서 저장한 Job 행은 별도 트랜잭션으로 커밋되므로,
 * API가 반환한 jobId가 실제 존재하는 행을 가리킨다(상세조회/재제출 정상 동작).</p>
 *
 * <p>jobId == null 이면 신규 제출(요청으로 엔티티 생성), null 아니면 재제출
 * (기존 행 로드 후 요청 데이터로 갱신). 트랜잭션 경계를 넘어 엔티티 인스턴스를
 * 공유하지 않고, 각 메서드가 자기 트랜잭션 안에서 엔티티를 새로 만들거나 로드한다.</p>
 */
@Service
@RequiredArgsConstructor
public class JobTrackingService {

    private final AdminContentRegisterJobRepository registerJobRepository;
    private final AdminContentUpdateJobRepository updateJobRepository;
    private final AdminContentDeleteJobRepository deleteJobRepository;
    private final AdminQuery adminQuery;

    // ===== 등록 =====

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long persistRegisterInvalid(Long jobId, Long adminId,
            AdminContentRegisterRequest req, List<JobValidationError> errors) {
        AdminContentRegisterJob job = loadOrCreateRegisterJob(jobId, adminId, req);
        job.clearErrors();
        job.setError("VALIDATION_ERROR", errors.get(0).message());
        job.setValidationErrors(errors);
        job.changeStatus(StreamingStatus.INVALID);
        job.finish();
        registerJobRepository.save(job);
        return job.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long persistRegisterFailed(Long jobId, Long adminId,
            AdminContentRegisterRequest req, Exception e) {
        AdminContentRegisterJob job = loadOrCreateRegisterJob(jobId, adminId, req);
        job.clearErrors();
        job.setError("PROCESSING_ERROR", e.getMessage());
        job.changeStatus(StreamingStatus.FAILED);
        job.finish();
        registerJobRepository.save(job);
        return job.getId();
    }

    private AdminContentRegisterJob loadOrCreateRegisterJob(Long jobId, Long adminId,
            AdminContentRegisterRequest req) {
        if (jobId == null) {
            return AdminContentMapper.toContentRegisterJob(req, adminId);
        }
        AdminContentRegisterJob job = adminQuery.findAdminContentRegisterJobById(jobId);
        job.updateFields(req.title(), req.description(), req.posterUrl(), req.backdropUrl(),
                req.trailerUrl(), req.openDate(), req.runningTime(), req.episode(), req.rating(),
                toCategoryMap(req.categories()), toPlatformMap(req.platforms()),
                req.directors(), req.casts(), req.countries());
        job.resetRetryCount();
        return job;
    }

    // ===== 수정 =====

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long persistUpdateInvalid(Long jobId, Long adminId, Long contentId,
            AdminContentUpdateRequest req, List<JobValidationError> errors) {
        AdminContentUpdateJob job = loadOrCreateUpdateJob(jobId, adminId, contentId, req);
        job.clearErrors();
        job.setError("VALIDATION_ERROR", errors.get(0).message());
        job.setValidationErrors(errors);
        job.changeStatus(StreamingStatus.INVALID);
        job.finish();
        updateJobRepository.save(job);
        return job.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long persistUpdateFailed(Long jobId, Long adminId, Long contentId,
            AdminContentUpdateRequest req, Exception e) {
        AdminContentUpdateJob job = loadOrCreateUpdateJob(jobId, adminId, contentId, req);
        job.clearErrors();
        job.setError("PROCESSING_ERROR", e.getMessage());
        job.changeStatus(StreamingStatus.FAILED);
        job.finish();
        updateJobRepository.save(job);
        return job.getId();
    }

    private AdminContentUpdateJob loadOrCreateUpdateJob(Long jobId, Long adminId, Long contentId,
            AdminContentUpdateRequest req) {
        if (jobId == null) {
            return AdminContentMapper.toContentUpdateJob(req, contentId, adminId);
        }
        AdminContentUpdateJob job = adminQuery.findAdminContentUpdateJobById(jobId);
        job.updateFields(req.title(), req.description(), req.posterUrl(), req.backdropUrl(),
                req.trailerUrl(), req.openDate(), req.runningTime(), req.episode(), req.rating(),
                toCategoryMap(req.categories()), toPlatformMap(req.platforms()),
                req.directors(), req.casts(), req.countries());
        job.resetRetryCount();
        return job;
    }

    // ===== 삭제 =====

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long persistDeleteInvalid(Long jobId, Long adminId, Long contentId,
            List<JobValidationError> errors) {
        AdminContentDeleteJob job = loadOrCreateDeleteJob(jobId, adminId, contentId);
        job.clearErrors();
        job.setError("VALIDATION_ERROR", errors.get(0).message());
        job.setValidationErrors(errors);
        job.changeStatus(StreamingStatus.INVALID);
        job.finish();
        deleteJobRepository.save(job);
        return job.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long persistDeleteFailed(Long jobId, Long adminId, Long contentId, Exception e) {
        AdminContentDeleteJob job = loadOrCreateDeleteJob(jobId, adminId, contentId);
        job.clearErrors();
        job.setError("PROCESSING_ERROR", e.getMessage());
        job.changeStatus(StreamingStatus.FAILED);
        job.finish();
        deleteJobRepository.save(job);
        return job.getId();
    }

    private AdminContentDeleteJob loadOrCreateDeleteJob(Long jobId, Long adminId, Long contentId) {
        if (jobId == null) {
            return AdminContentMapper.toContentDeleteJob(contentId, adminId);
        }
        AdminContentDeleteJob job = adminQuery.findAdminContentDelJobById(jobId);
        job.updateContentId(contentId);
        job.resetRetryCount();
        return job;
    }

    // ===== 공통 =====

    static Map<String, AdminCategoryDTO> toCategoryMap(List<AdminCategoryDTO> categories) {
        Map<String, AdminCategoryDTO> map = new HashMap<>();
        categories.forEach(dto -> map.put(dto.categoryType(), dto));
        return map;
    }

    static Map<String, AdminPlatformDTO> toPlatformMap(List<AdminPlatformDTO> platforms) {
        Map<String, AdminPlatformDTO> map = new HashMap<>();
        platforms.forEach(dto -> map.put(dto.platformType(), dto));
        return map;
    }
}
