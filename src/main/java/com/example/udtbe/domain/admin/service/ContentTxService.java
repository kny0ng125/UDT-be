package com.example.udtbe.domain.admin.service;

import com.example.udtbe.domain.admin.dto.AdminContentMapper;
import com.example.udtbe.domain.admin.dto.request.AdminContentRegisterRequest;
import com.example.udtbe.domain.admin.dto.request.AdminContentUpdateRequest;
import com.example.udtbe.domain.streaming.entity.AdminContentDeleteJob;
import com.example.udtbe.domain.streaming.entity.AdminContentRegisterJob;
import com.example.udtbe.domain.streaming.entity.AdminContentUpdateJob;
import com.example.udtbe.domain.streaming.entity.enums.StreamingStatus;
import com.example.udtbe.domain.streaming.repository.AdminContentDeleteJobRepository;
import com.example.udtbe.domain.streaming.repository.AdminContentRegisterJobRepository;
import com.example.udtbe.domain.streaming.repository.AdminContentUpdateJobRepository;
import com.example.udtbe.domain.content.entity.Content;
import com.example.udtbe.domain.content.entity.ContentMetadata;
import com.example.udtbe.domain.content.event.ContentStreamingEvent;
import com.example.udtbe.domain.content.event.ContentStreamingType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 콘텐츠 등록/수정/삭제 + Job COMPLETED + 스트리밍 이벤트 발행을 <b>단일 트랜잭션</b>으로
 * 원자 처리한다. "Job 완료인데 콘텐츠 없음" 같은 불일치를 원천 차단한다.
 *
 * <p>이벤트는 이 트랜잭션 안에서 발행되므로 {@code @TransactionalEventListener(AFTER_COMMIT)}가
 * 콘텐츠 커밋을 기준으로 동작한다.</p>
 *
 * <p>{@link AdminService}의 콘텐츠 도메인 로직(registerContent/updateContent/deleteContent)을
 * 재사용한다. 이로 인한 {@code AdminService ↔ ContentTxService} 생성자 순환은
 * {@code @Lazy}로 차단한다.</p>
 */
@Service
public class ContentTxService {

    private final AdminService adminService;
    private final AdminQuery adminQuery;
    private final ApplicationEventPublisher eventPublisher;
    private final AdminContentRegisterJobRepository registerJobRepository;
    private final AdminContentUpdateJobRepository updateJobRepository;
    private final AdminContentDeleteJobRepository deleteJobRepository;

    public ContentTxService(
            @Lazy AdminService adminService,
            AdminQuery adminQuery,
            ApplicationEventPublisher eventPublisher,
            AdminContentRegisterJobRepository registerJobRepository,
            AdminContentUpdateJobRepository updateJobRepository,
            AdminContentDeleteJobRepository deleteJobRepository) {
        this.adminService = adminService;
        this.adminQuery = adminQuery;
        this.eventPublisher = eventPublisher;
        this.registerJobRepository = registerJobRepository;
        this.updateJobRepository = updateJobRepository;
        this.deleteJobRepository = deleteJobRepository;
    }

    @Transactional
    public Long processRegisterAndComplete(Long jobId, Long adminId,
            AdminContentRegisterRequest req) {
        Content content = adminService.registerContent(req);

        ContentMetadata metadata = adminQuery.findContentMetadataByContentId(content.getId());
        eventPublisher.publishEvent(ContentStreamingEvent.of(
                this, ContentStreamingType.REGISTER, content.getId(), metadata));

        AdminContentRegisterJob job;
        if (jobId == null) {
            job = AdminContentMapper.toContentRegisterJob(req, adminId);
        } else {
            job = adminQuery.findAdminContentRegisterJobById(jobId);
            job.updateFields(req.title(), req.description(), req.posterUrl(), req.backdropUrl(),
                    req.trailerUrl(), req.openDate(), req.runningTime(), req.episode(),
                    req.rating(),
                    JobTrackingService.toCategoryMap(req.categories()),
                    JobTrackingService.toPlatformMap(req.platforms()),
                    req.directors(), req.casts(), req.countries());
            job.clearErrors();
            job.resetRetryCount();
        }
        job.changeStatus(StreamingStatus.COMPLETED);
        job.finish();
        registerJobRepository.save(job);
        return job.getId();
    }

    @Transactional
    public Long processUpdateAndComplete(Long jobId, Long adminId, Long contentId,
            AdminContentUpdateRequest req) {
        adminService.updateContent(contentId, req);

        ContentMetadata metadata = adminQuery.findContentMetadataByContentId(contentId);
        eventPublisher.publishEvent(ContentStreamingEvent.of(
                this, ContentStreamingType.UPDATE, contentId, metadata));

        AdminContentUpdateJob job;
        if (jobId == null) {
            job = AdminContentMapper.toContentUpdateJob(req, contentId, adminId);
        } else {
            job = adminQuery.findAdminContentUpdateJobById(jobId);
            job.updateFields(req.title(), req.description(), req.posterUrl(), req.backdropUrl(),
                    req.trailerUrl(), req.openDate(), req.runningTime(), req.episode(),
                    req.rating(),
                    JobTrackingService.toCategoryMap(req.categories()),
                    JobTrackingService.toPlatformMap(req.platforms()),
                    req.directors(), req.casts(), req.countries());
            job.clearErrors();
            job.resetRetryCount();
        }
        job.changeStatus(StreamingStatus.COMPLETED);
        job.finish();
        updateJobRepository.save(job);
        return job.getId();
    }

    @Transactional
    public Long processDeleteAndComplete(Long jobId, Long adminId, Long contentId) {
        adminService.deleteContent(contentId);

        eventPublisher.publishEvent(ContentStreamingEvent.of(
                this, ContentStreamingType.DELETE, contentId, null));

        AdminContentDeleteJob job;
        if (jobId == null) {
            job = AdminContentMapper.toContentDeleteJob(contentId, adminId);
        } else {
            job = adminQuery.findAdminContentDelJobById(jobId);
            job.updateContentId(contentId);
            job.clearErrors();
            job.resetRetryCount();
        }
        job.changeStatus(StreamingStatus.COMPLETED);
        job.finish();
        deleteJobRepository.save(job);
        return job.getId();
    }
}
