package com.example.udtbe.domain.admin.dto;

import com.example.udtbe.domain.admin.dto.common.AdminCategoryDTO;
import com.example.udtbe.domain.admin.dto.common.AdminMemberGenreFeedbackDTO;
import com.example.udtbe.domain.admin.dto.common.AdminPlatformDTO;
import com.example.udtbe.domain.admin.dto.request.AdminContentRegisterRequest;
import com.example.udtbe.domain.admin.dto.request.AdminContentUpdateRequest;
import com.example.udtbe.domain.admin.dto.response.AdminContentDelJobGetDetailResponse;
import com.example.udtbe.domain.admin.dto.response.AdminContentDeleteResponse;
import com.example.udtbe.domain.admin.dto.response.AdminContentRegJobGetDetailResponse;
import com.example.udtbe.domain.admin.dto.response.AdminContentRegisterResponse;
import com.example.udtbe.domain.admin.dto.response.AdminContentUpJobGetDetailResponse;
import com.example.udtbe.domain.admin.dto.response.AdminContentUpdateResponse;
import com.example.udtbe.domain.admin.dto.response.AdminScheduledContentResultGetResponse;
import com.example.udtbe.domain.streaming.entity.AdminContentDeleteJob;
import com.example.udtbe.domain.streaming.entity.AdminContentRegisterJob;
import com.example.udtbe.domain.streaming.entity.AdminContentUpdateJob;
import com.example.udtbe.domain.streaming.entity.StreamingJobMetric;
import com.example.udtbe.domain.streaming.entity.enums.StreamingJobStatus;
import com.example.udtbe.domain.streaming.entity.enums.StreamingJobType;
import com.example.udtbe.domain.streaming.entity.enums.StreamingStatus;
import com.example.udtbe.domain.content.entity.Content;
import com.example.udtbe.domain.content.entity.FeedbackStatistics;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AdminContentMapper {

    public static AdminContentRegisterJob toContentRegisterJob(AdminContentRegisterRequest request,
            Long memberId) {

        Map<String, AdminCategoryDTO> categoryDTOs = new HashMap<>();
        request.categories().forEach(dto -> categoryDTOs.put(dto.categoryType(), dto));

        Map<String, AdminPlatformDTO> platformDTOs = new HashMap<>();
        request.platforms().forEach(dto -> platformDTOs.put(dto.platformType(), dto));

        return AdminContentRegisterJob.of(
                StreamingStatus.PENDING,
                memberId,
                request.title(),
                request.description(),
                request.posterUrl(),
                request.backdropUrl(),
                request.trailerUrl(),
                request.openDate(),
                request.runningTime(),
                request.episode(),
                request.rating(),
                categoryDTOs,
                platformDTOs,
                request.casts(),
                request.directors(),
                request.countries()
        );
    }

    public static AdminContentUpdateJob toContentUpdateJob(AdminContentUpdateRequest request,
            Long contentId, Long memberId) {

        Map<String, AdminCategoryDTO> categoryDTOs = new HashMap<>();
        request.categories().forEach(dto -> categoryDTOs.put(dto.categoryType(), dto));

        Map<String, AdminPlatformDTO> platformDTOs = new HashMap<>();
        request.platforms().forEach(dto -> platformDTOs.put(dto.platformType(), dto));

        return AdminContentUpdateJob.of(
                StreamingStatus.PENDING,
                memberId,
                contentId,
                request.title(),
                request.description(),
                request.posterUrl(),
                request.backdropUrl(),
                request.trailerUrl(),
                request.openDate(),
                request.runningTime(),
                request.episode(),
                request.rating(),
                categoryDTOs,
                platformDTOs,
                request.casts(),
                request.directors(),
                request.countries()
        );
    }

    public static AdminContentDeleteJob toContentDeleteJob(Long contentId, Long memberId) {
        return AdminContentDeleteJob.of(
                StreamingStatus.PENDING,
                memberId,
                contentId
        );
    }

    public static AdminContentRegisterRequest toContentRegisterRequest(
            AdminContentRegisterJob job) {
        List<AdminCategoryDTO> categoryDTOs = new ArrayList<>();
        job.getCategories().forEach((e, v) ->
                categoryDTOs.add(v)
        );
        List<AdminPlatformDTO> platformDTOs = new ArrayList<>();
        job.getPlatforms().forEach((e, v) ->
                platformDTOs.add(v)
        );
        return new AdminContentRegisterRequest(
                job.getTitle(),
                job.getDescription(),
                job.getPosterUrl(),
                job.getBackdropUrl(),
                job.getTrailerUrl(),
                job.getOpenDate(),
                job.getRunningTime(),
                job.getEpisode(),
                job.getRating(),
                categoryDTOs,
                job.getCountries(),
                job.getDirectors(),
                job.getCasts(),
                platformDTOs
        );
    }

    public static AdminContentUpdateRequest toContentUpdateRequest(AdminContentUpdateJob job) {

        List<AdminCategoryDTO> categoryDTOs = new ArrayList<>();
        job.getCategories().forEach((e, v) ->
                categoryDTOs.add(v)
        );
        List<AdminPlatformDTO> platformDTOs = new ArrayList<>();
        job.getPlatforms().forEach((e, v) ->
                platformDTOs.add(v)
        );

        return new AdminContentUpdateRequest(
                job.getTitle(),
                job.getDescription(),
                job.getPosterUrl(),
                job.getBackdropUrl(),
                job.getTrailerUrl(),
                job.getOpenDate(),
                job.getRunningTime(),
                job.getEpisode(),
                job.getRating(),
                categoryDTOs,
                job.getCountries(),
                job.getDirectors(),
                job.getCasts(),
                platformDTOs
        );
    }

    public static AdminContentRegisterResponse toContentRegisterResponse(Long jobId) {
        return new AdminContentRegisterResponse(jobId);
    }

    public static AdminContentUpdateResponse toContentUpdateResponse(Long jobId) {
        return new AdminContentUpdateResponse(jobId);
    }

    public static AdminContentDeleteResponse toContentDeleteResponse(Long jobId) {
        return new AdminContentDeleteResponse(jobId);
    }

    public static AdminContentRegJobGetDetailResponse toAdminContentRegJobDetailResponse(
            AdminContentRegisterJob job) {
        List<AdminCategoryDTO> categoryDTOs = new ArrayList<>(job.getCategories().values());
        List<AdminPlatformDTO> platformDTOs = new ArrayList<>(job.getPlatforms().values());

        return new AdminContentRegJobGetDetailResponse(
                job.getStreamingJobMetricId(),
                job.getStatus(),
                job.getTitle(),
                job.getDescription(),
                job.getPosterUrl(),
                job.getBackdropUrl(),
                job.getTrailerUrl(),
                job.getOpenDate(),
                job.getRunningTime(),
                job.getEpisode(),
                job.getRating(),
                categoryDTOs,
                job.getCountries(),
                job.getCasts(),
                job.getDirectors(),
                platformDTOs,
                job.getErrorMessage(),
                job.getValidationErrors(),
                job.getRetryCount(),
                job.getSkipCount()
        );
    }

    public static AdminContentUpJobGetDetailResponse toAdminContentUpdateJobDetailResponse(
            AdminContentUpdateJob job) {
        List<AdminCategoryDTO> categoryDTOs = new ArrayList<>(job.getCategories().values());
        List<AdminPlatformDTO> platformDTOs = new ArrayList<>(job.getPlatforms().values());

        return new AdminContentUpJobGetDetailResponse(
                job.getStreamingJobMetricId(),
                job.getStatus(),
                job.getContentId(),
                job.getTitle(),
                job.getDescription(),
                job.getPosterUrl(),
                job.getBackdropUrl(),
                job.getTrailerUrl(),
                job.getOpenDate(),
                job.getRunningTime(),
                job.getEpisode(),
                job.getRating(),
                categoryDTOs,
                job.getCountries(),
                job.getCasts(),
                job.getDirectors(),
                platformDTOs,
                job.getErrorMessage(),
                job.getValidationErrors(),
                job.getRetryCount(),
                job.getSkipCount()
        );
    }

    public static AdminContentDelJobGetDetailResponse toAdminContentDelJobDetailResponse(
            AdminContentDeleteJob job) {

        return new AdminContentDelJobGetDetailResponse(
                job.getStreamingJobMetricId(),
                job.getStatus(),
                job.getContentId(),
                job.getErrorMessage(),
                job.getValidationErrors(),
                job.getRetryCount(),
                job.getSkipCount()
        );
    }

    public static StreamingJobMetric initStreamingJobMetric(StreamingJobType streamingJobType) {
        return StreamingJobMetric.of(
                streamingJobType,
                StreamingJobStatus.NOOP,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    public static AdminScheduledContentResultGetResponse toAdminScheduledContentResultGetResponse(
            StreamingJobMetric metric) {
        return new AdminScheduledContentResultGetResponse(
                metric.getId(),
                metric.getType(),
                metric.getStatus(),
                metric.getTotalRead(),
                metric.getTotalComplete(),
                metric.getTotalInvalid(),
                metric.getTotalFailed(),
                metric.getStartTime(),
                metric.getEndTime()
        );
    }

    public static Content toContentEntity(AdminContentRegisterRequest adminContentRegisterRequest) {
        return Content.of(
                adminContentRegisterRequest.title(),
                adminContentRegisterRequest.description(),
                adminContentRegisterRequest.posterUrl(),
                adminContentRegisterRequest.backdropUrl(),
                adminContentRegisterRequest.trailerUrl(),
                adminContentRegisterRequest.openDate(),
                adminContentRegisterRequest.runningTime(),
                adminContentRegisterRequest.episode(),
                adminContentRegisterRequest.rating()
        );
    }


    public static List<AdminMemberGenreFeedbackDTO> toGenreFeedbackDtoList(
            List<FeedbackStatistics> stats) {

        return stats.stream()
                .map(fs -> new AdminMemberGenreFeedbackDTO(
                        fs.getGenreType(),
                        fs.getLikeCount(),
                        fs.getDislikeCount(),
                        fs.getUninterestedCount()))
                .toList();
    }


}
