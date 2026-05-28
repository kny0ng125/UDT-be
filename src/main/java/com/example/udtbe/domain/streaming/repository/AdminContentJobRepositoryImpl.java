package com.example.udtbe.domain.streaming.repository;

import static com.example.udtbe.domain.streaming.entity.QAdminContentDeleteJob.adminContentDeleteJob;
import static com.example.udtbe.domain.streaming.entity.QAdminContentRegisterJob.adminContentRegisterJob;
import static com.example.udtbe.domain.streaming.entity.QAdminContentUpdateJob.adminContentUpdateJob;
import static com.example.udtbe.domain.streaming.entity.QStreamingJobMetric.streamingJobMetric;

import com.example.udtbe.domain.admin.dto.AdminContentMapper;
import com.example.udtbe.domain.admin.dto.common.StreamingJobMetricDTO;
import com.example.udtbe.domain.admin.dto.response.AdminScheduledContentMetricGetResponse;
import com.example.udtbe.domain.admin.dto.response.AdminScheduledContentResponse;
import com.example.udtbe.domain.admin.dto.response.AdminScheduledContentResultGetResponse;
import com.example.udtbe.domain.streaming.entity.StreamingJobMetric;
import com.example.udtbe.domain.streaming.entity.enums.StreamingFilterType;
import com.example.udtbe.domain.streaming.entity.enums.StreamingJobType;
import com.example.udtbe.domain.streaming.entity.enums.StreamingStatus;
import com.example.udtbe.domain.streaming.exception.StreamingErrorCode;
import com.example.udtbe.global.dto.CursorPageResponse;
import com.example.udtbe.global.exception.RestApiException;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
@Slf4j
public class AdminContentJobRepositoryImpl implements AdminContentJobRepositoryCustom {

    private final JPAQueryFactory jpaQueryFactory;

    @PersistenceContext
    private final EntityManager em;

    @Override
    public CursorPageResponse<AdminScheduledContentResponse> getJobsByCursor(String cursor,
            int size,
            StreamingFilterType type) {

        long jobId = Long.MAX_VALUE;
        LocalDateTime createdAt = LocalDateTime.now();
        String jobType = StreamingJobType.REGISTER.name();

        if (StringUtils.hasText(cursor)) {
            String[] parts = cursor.split("\\|");
            if (parts.length == 3) {
                jobId = Long.parseLong(parts[0]);
                createdAt = LocalDateTime.parse(parts[1]);
                jobType = parts[2];
            } else {
                throw new RestApiException(StreamingErrorCode.CURSOR_BAD_REQUEST);
            }
        }

        String statusCondition = "";
        if (StreamingFilterType.FAILED.equals(type)) {
            statusCondition = "status = 'FAILED' AND\n";
        } else if (StreamingFilterType.PENDING.equals(type)) {
            statusCondition = "status = 'PENDING'  AND\n";
        } else if (StreamingFilterType.INVALID.equals(type)) {
            statusCondition = "status = 'INVALID'  AND\n";
        }

        String sql = """
                SELECT id, status, admin_id, created_at, scheduled_at, finished_at, job_type
                FROM (
                    SELECT admin_content_register_job_id AS id, status, admin_id, created_at, scheduled_at, finished_at, 'REGISTER' AS job_type
                    FROM admin_content_register_job
                    UNION ALL
                    SELECT admin_content_update_job_id AS id, status, admin_id, created_at, scheduled_at, finished_at, 'UPDATE' AS job_type
                    FROM admin_content_update_job
                    UNION ALL
                    SELECT admin_content_delete_job_id AS id, status, admin_id, created_at, scheduled_at, finished_at, 'DELETE' AS job_type
                    FROM admin_content_delete_job
                ) AS jobs
                WHERE (
                """ + statusCondition + """
                    (
                        created_at < :createdAt
                        OR (created_at = :createdAt AND job_type < :jobType)
                        OR (created_at = :createdAt AND job_type = :jobType AND id < :jobId)
                    )
                )
                ORDER BY created_at DESC,
                    CASE job_type
                    WHEN 'REGISTER' THEN 3
                    WHEN 'UPDATE' THEN 2
                    WHEN 'DELETE' THEN 1
                    ELSE 0
                    END DESC,
                    id DESC
                LIMIT :limit ;
                """;

        List<Object[]> resultList = em.createNativeQuery(sql)
                .setParameter("createdAt", createdAt)
                .setParameter("jobType", jobType)
                .setParameter("jobId", jobId)
                .setParameter("limit", size + 1)
                .getResultList();

        List<AdminScheduledContentResponse> results = resultList.stream()
                .map(row -> new AdminScheduledContentResponse(
                        ((Number) row[0]).longValue(),
                        StreamingStatus.from((String) row[1]),
                        ((Number) row[2]).longValue(),
                        ((Timestamp) row[3]).toLocalDateTime(),
                        ((Timestamp) row[4]).toLocalDateTime(),
                        ((row[5] != null) ? ((Timestamp) row[5]).toLocalDateTime() : null),
                        StreamingJobType.from((String) row[6])
                )).toList();

        String nextCursor = null;
        if (results.size() > size) {
            AdminScheduledContentResponse last = results.get(size - 1);
            nextCursor = String.format("%d|%s|%s",
                    last.id(),
                    last.createdAt(),
                    last.jobType());
        }

        return new CursorPageResponse<>(results, nextCursor, results.size() < size + 1);
    }

    @Override
    public StreamingJobMetricDTO getContentRegisterJobMetrics(Long metricId) {

        List<Tuple> counts = jpaQueryFactory
                .select(adminContentRegisterJob.status, adminContentRegisterJob.id.count())
                .from(adminContentRegisterJob)
                .where(adminContentRegisterJob.status.in(
                        StreamingStatus.COMPLETED,
                        StreamingStatus.FAILED,
                        StreamingStatus.INVALID).and(
                        adminContentRegisterJob.streamingJobMetricId.eq(metricId)))
                .groupBy(adminContentRegisterJob.status)
                .fetch();

        Map<StreamingStatus, Long> countMap = counts.stream()
                .collect(Collectors.toMap(
                        tuple -> tuple.get(adminContentRegisterJob.status),
                        tuple -> Optional.ofNullable(tuple.get(adminContentRegisterJob.id.count()))
                                .orElse(0L)
                ));

        long totalCompleted = countMap.getOrDefault(StreamingStatus.COMPLETED, 0L);
        long totalFailed = countMap.getOrDefault(StreamingStatus.FAILED, 0L);
        long totalInvalid = countMap.getOrDefault(StreamingStatus.INVALID, 0L);
        long totalRead = totalCompleted + totalFailed + totalInvalid;

        return new StreamingJobMetricDTO(totalRead, totalCompleted, totalInvalid, totalFailed);
    }


    @Override
    public StreamingJobMetricDTO getContentUpdateJobMetrics(Long metricId) {

        List<Tuple> counts = jpaQueryFactory
                .select(adminContentUpdateJob.status, adminContentUpdateJob.id.count())
                .from(adminContentUpdateJob)
                .where(adminContentUpdateJob.status.in(
                        StreamingStatus.COMPLETED,
                        StreamingStatus.FAILED,
                        StreamingStatus.INVALID).and(
                        adminContentUpdateJob.streamingJobMetricId.eq(metricId)))
                .groupBy(adminContentUpdateJob.status)
                .fetch();

        Map<StreamingStatus, Long> countMap = counts.stream()
                .collect(Collectors.toMap(
                        tuple -> tuple.get(adminContentUpdateJob.status),
                        tuple -> Optional.ofNullable(tuple.get(adminContentUpdateJob.id.count()))
                                .orElse(0L)
                ));

        long totalCompleted = countMap.getOrDefault(StreamingStatus.COMPLETED, 0L);
        long totalFailed = countMap.getOrDefault(StreamingStatus.FAILED, 0L);
        long totalInvalid = countMap.getOrDefault(StreamingStatus.INVALID, 0L);
        long totalRead = totalCompleted + totalFailed + totalInvalid;

        return new StreamingJobMetricDTO(totalRead, totalCompleted, totalInvalid, totalFailed);
    }

    @Override
    public StreamingJobMetricDTO getContentDeleteJobMetrics(Long metricId) {

        List<Tuple> counts = jpaQueryFactory
                .select(adminContentDeleteJob.status, adminContentDeleteJob.id.count())
                .from(adminContentDeleteJob)
                .where(adminContentDeleteJob.status.in(
                        StreamingStatus.COMPLETED,
                        StreamingStatus.FAILED,
                        StreamingStatus.INVALID).and(
                        adminContentDeleteJob.streamingJobMetricId.eq(metricId)))
                .groupBy(adminContentDeleteJob.status)
                .fetch();

        Map<StreamingStatus, Long> countMap = counts.stream()
                .collect(Collectors.toMap(
                        tuple -> tuple.get(adminContentDeleteJob.status),
                        tuple -> Optional.ofNullable(tuple.get(adminContentDeleteJob.id.count()))
                                .orElse(0L)
                ));

        long totalCompleted = countMap.getOrDefault(StreamingStatus.COMPLETED, 0L);
        long totalFailed = countMap.getOrDefault(StreamingStatus.FAILED, 0L);
        long totalInvalid = countMap.getOrDefault(StreamingStatus.INVALID, 0L);
        long totalRead = totalCompleted + totalFailed + totalInvalid;

        return new StreamingJobMetricDTO(totalRead, totalCompleted, totalInvalid, totalFailed);
    }

    @Override
    public AdminScheduledContentMetricGetResponse getScheduledContentMetrics() {

        Tuple result = jpaQueryFactory
                .select(
                        streamingJobMetric.totalRead.sumBigInteger().coalesce(BigInteger.ZERO),
                        streamingJobMetric.totalComplete.sumBigInteger().coalesce(BigInteger.ZERO),
                        streamingJobMetric.totalInvalid.sumBigInteger().coalesce(BigInteger.ZERO),
                        streamingJobMetric.totalFailed.sumBigInteger().coalesce(BigInteger.ZERO)
                )
                .from(streamingJobMetric)
                .fetchOne();

        long totalRead = Objects.requireNonNull(result.get(0, BigInteger.class)).longValue();
        long totalComplete = Objects.requireNonNull(result.get(1, BigInteger.class)).longValue();
        long totalInvalid = Objects.requireNonNull(result.get(2, BigInteger.class)).longValue();
        long totalFailed = Objects.requireNonNull(result.get(3, BigInteger.class)).longValue();

        return new AdminScheduledContentMetricGetResponse(
                totalRead,
                totalComplete,
                totalInvalid,
                totalFailed
        );
    }

    @Override
    public CursorPageResponse<AdminScheduledContentResultGetResponse> getScheduledContentResults(
            String cursor, int size) {

        Long cursorId;
        if (!StringUtils.hasText(cursor)) {
            cursorId = null;
        } else {
            try {
                cursorId = Long.parseLong(cursor);
            } catch (NumberFormatException e) {
                throw new RestApiException(StreamingErrorCode.CURSOR_BAD_REQUEST);
            }
        }

        List<StreamingJobMetric> results = jpaQueryFactory
                .selectFrom(streamingJobMetric)
                .where(cursorId != null ? streamingJobMetric.id.lt(cursorId) : null)
                .orderBy(streamingJobMetric.id.desc())
                .limit(size + 1)
                .fetch();

        boolean hasNext = results.size() > size;
        if (hasNext) {
            results = results.subList(0, size);
        }

        List<AdminScheduledContentResultGetResponse> responses = results.stream()
                .map(AdminContentMapper::toAdminScheduledContentResultGetResponse
                )
                .toList();

        String nextCursor = hasNext && !results.isEmpty()
                ? results.get(results.size() - 1).getId().toString()
                : null;

        return new CursorPageResponse<>(responses, nextCursor, hasNext);
    }


}