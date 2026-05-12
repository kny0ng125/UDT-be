package com.example.udtbe.domain.admin.service;

import com.example.udtbe.domain.admin.dto.AdminContentMapper;
import com.example.udtbe.domain.admin.dto.request.AdminContentRegisterRequest;
import com.example.udtbe.domain.admin.dto.request.AdminContentUpdateRequest;
import com.example.udtbe.domain.batch.dto.JobValidationError;
import com.example.udtbe.domain.batch.entity.AdminContentDeleteJob;
import com.example.udtbe.domain.batch.entity.AdminContentRegisterJob;
import com.example.udtbe.domain.batch.entity.AdminContentUpdateJob;
import com.example.udtbe.domain.batch.entity.enums.BatchStatus;
import com.example.udtbe.domain.batch.repository.AdminContentDeleteJobRepository;
import com.example.udtbe.domain.batch.repository.AdminContentRegisterJobRepository;
import com.example.udtbe.domain.batch.repository.AdminContentUpdateJobRepository;
import com.example.udtbe.domain.content.entity.Content;
import com.example.udtbe.domain.content.entity.ContentMetadata;
import com.example.udtbe.domain.content.event.ContentStreamingEvent;
import com.example.udtbe.domain.content.event.ContentStreamingType;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminTriggerService {

    public static final int MAX_RETRY_COUNT = 3;

    private final AdminContentRegisterJobRepository registerJobRepository;
    private final AdminContentUpdateJobRepository updateJobRepository;
    private final AdminContentDeleteJobRepository deleteJobRepository;
    private final AdminService adminService;
    private final AdminQuery adminQuery;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void retryFailedBatch() {
        List<AdminContentRegisterJob> failedRegJobs =
                registerJobRepository.findByStatusAndRetryCountLessThan(
                        BatchStatus.FAILED, MAX_RETRY_COUNT);
        failedRegJobs.forEach(this::retryRegisterJob);

        List<AdminContentUpdateJob> failedUpJobs =
                updateJobRepository.findByStatusAndRetryCountLessThan(
                        BatchStatus.FAILED, MAX_RETRY_COUNT);
        failedUpJobs.forEach(this::retryUpdateJob);

        List<AdminContentDeleteJob> failedDelJobs =
                deleteJobRepository.findByStatusAndRetryCountLessThan(
                        BatchStatus.FAILED, MAX_RETRY_COUNT);
        failedDelJobs.forEach(this::retryDeleteJob);

        log.info("실패 건 재처리 완료: 등록={}, 수정={}, 삭제={}",
                failedRegJobs.size(), failedUpJobs.size(), failedDelJobs.size());
    }

    private void retryRegisterJob(AdminContentRegisterJob job) {
        job.incrementRetryCount();
        job.changeStatus(BatchStatus.PROCESSING);
        try {
            AdminContentRegisterRequest request =
                    AdminContentMapper.toContentRegisterRequest(job);

            List<JobValidationError> errors = adminQuery.collectValidationErrors(
                    request.categories(), request.platforms(),
                    request.casts(), request.directors());

            if (!errors.isEmpty()) {
                job.changeStatus(BatchStatus.INVALID);
                job.setError("VALIDATION_ERROR", errors.get(0).message());
                job.setValidationErrors(errors);
                job.finish();
                log.warn("등록 Job 재시도 INVALID - jobId={}, errors={}", job.getId(), errors.size());
                return;
            }

            Content content = adminService.registerContent(request);

            ContentMetadata metadata = adminQuery.findContentMetadataByContentId(content.getId());
            eventPublisher.publishEvent(ContentStreamingEvent.of(
                    this, ContentStreamingType.REGISTER, content.getId(), metadata));

            job.changeStatus(BatchStatus.COMPLETED);
            job.finish();
        } catch (Exception e) {
            job.changeStatus(BatchStatus.FAILED);
            job.setError("RETRY_FAILED", e.getMessage());
            job.finish();
            log.warn("등록 Job 재시도 실패 - jobId={} (retry {}/{}): {}",
                    job.getId(), job.getRetryCount(), MAX_RETRY_COUNT, e.getMessage());
        }
    }

    private void retryUpdateJob(AdminContentUpdateJob job) {
        job.incrementRetryCount();
        job.changeStatus(BatchStatus.PROCESSING);
        try {
            AdminContentUpdateRequest request =
                    AdminContentMapper.toContentUpdateRequest(job);

            List<JobValidationError> errors = new ArrayList<>(
                    adminQuery.collectContentIdValidationError(job.getContentId()));
            errors.addAll(adminQuery.collectValidationErrors(
                    request.categories(), request.platforms(),
                    request.casts(), request.directors()));

            if (!errors.isEmpty()) {
                job.changeStatus(BatchStatus.INVALID);
                job.setError("VALIDATION_ERROR", errors.get(0).message());
                job.setValidationErrors(errors);
                job.finish();
                log.warn("수정 Job 재시도 INVALID - jobId={}, errors={}", job.getId(), errors.size());
                return;
            }

            adminService.updateContent(job.getContentId(), request);

            ContentMetadata metadata = adminQuery.findContentMetadataByContentId(
                    job.getContentId());
            eventPublisher.publishEvent(ContentStreamingEvent.of(
                    this, ContentStreamingType.UPDATE, job.getContentId(), metadata));

            job.changeStatus(BatchStatus.COMPLETED);
            job.finish();
        } catch (Exception e) {
            job.changeStatus(BatchStatus.FAILED);
            job.setError("RETRY_FAILED", e.getMessage());
            job.finish();
            log.warn("수정 Job 재시도 실패 - jobId={} (retry {}/{}): {}",
                    job.getId(), job.getRetryCount(), MAX_RETRY_COUNT, e.getMessage());
        }
    }

    private void retryDeleteJob(AdminContentDeleteJob job) {
        job.incrementRetryCount();
        job.changeStatus(BatchStatus.PROCESSING);
        try {
            List<JobValidationError> errors = adminQuery.collectContentIdValidationError(
                    job.getContentId());

            if (!errors.isEmpty()) {
                job.changeStatus(BatchStatus.INVALID);
                job.setError("VALIDATION_ERROR", errors.get(0).message());
                job.setValidationErrors(errors);
                job.finish();
                log.warn("삭제 Job 재시도 INVALID - jobId={}, errors={}", job.getId(), errors.size());
                return;
            }

            adminService.deleteContent(job.getContentId());

            eventPublisher.publishEvent(ContentStreamingEvent.of(
                    this, ContentStreamingType.DELETE, job.getContentId(), null));

            job.changeStatus(BatchStatus.COMPLETED);
            job.finish();
        } catch (Exception e) {
            job.changeStatus(BatchStatus.FAILED);
            job.setError("RETRY_FAILED", e.getMessage());
            job.finish();
            log.warn("삭제 Job 재시도 실패 - jobId={} (retry {}/{}): {}",
                    job.getId(), job.getRetryCount(), MAX_RETRY_COUNT, e.getMessage());
        }
    }
}
