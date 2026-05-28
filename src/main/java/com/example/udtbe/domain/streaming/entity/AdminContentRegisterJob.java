package com.example.udtbe.domain.streaming.entity;

import static lombok.AccessLevel.PRIVATE;

import com.example.udtbe.domain.admin.dto.common.AdminCategoryDTO;
import com.example.udtbe.domain.admin.dto.common.AdminPlatformDTO;
import com.example.udtbe.domain.streaming.dto.JobValidationError;
import com.example.udtbe.domain.streaming.entity.enums.StreamingStatus;
import com.example.udtbe.domain.streaming.util.TimeUtil;
import com.example.udtbe.global.entity.TimeBaseEntity;
import com.example.udtbe.global.util.OptionalLongConverter;
import com.example.udtbe.global.util.OptionalTagConverter;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;


@Entity
@Getter
@Table(name = "admin_content_register_job")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminContentRegisterJob extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admin_content_register_job_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    private StreamingStatus status;

    private LocalDateTime scheduledAt;

    private LocalDateTime finishedAt;

    private Long adminId;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String posterUrl;

    private String backdropUrl;

    private String trailerUrl;

    private LocalDateTime openDate;

    private int runningTime;

    private int episode;

    private String rating;

    @Type(JsonType.class)
    @Column(name = "categorys", columnDefinition = "longtext")
    private Map<String, AdminCategoryDTO> categories;

    @Type(JsonType.class)
    @Column(name = "platforms", columnDefinition = "longtext")
    private Map<String, AdminPlatformDTO> platforms;

    @Convert(converter = OptionalLongConverter.class)
    @Column(name = "directors")
    private List<Long> directors;

    @Convert(converter = OptionalLongConverter.class)
    @Column(name = "casts")
    private List<Long> casts;

    @Convert(converter = OptionalTagConverter.class)
    @Column(name = "countries")
    private List<String> countries;

    private String errorCode;

    private String errorMessage;

    @Type(JsonType.class)
    @Column(name = "validation_errors", columnDefinition = "longtext")
    private List<JobValidationError> validationErrors = new ArrayList<>();

    private int retryCount = 0;

    private int skipCount = 0;

    @Column(name = "batch_job_metric_id")
    private Long streamingJobMetricId;

    @Builder(access = PRIVATE)
    private AdminContentRegisterJob(StreamingStatus status, Long adminId,
            LocalDateTime scheduledAt,
            String title, String description, String posterUrl, String backdropUrl,
            String trailerUrl,
            LocalDateTime openDate, int runningTime, int episode, String rating,
            Map<String, AdminCategoryDTO> categories, Map<String, AdminPlatformDTO> platforms,
            List<Long> directors, List<Long> casts, List<String> countries) {

        this.status = status;
        this.scheduledAt = scheduledAt;
        this.adminId = adminId;
        this.title = title;
        this.description = description;
        this.posterUrl = posterUrl;
        this.backdropUrl = backdropUrl;
        this.trailerUrl = trailerUrl;
        this.openDate = openDate;
        this.runningTime = runningTime;
        this.episode = episode;
        this.rating = rating;
        this.categories = categories;
        this.platforms = platforms;
        this.directors = directors;
        this.casts = casts;
        this.countries = countries;
    }

    public static AdminContentRegisterJob of(StreamingStatus streamingStepStatus, Long adminId,
            String title, String description, String posterUrl, String backdropUrl,
            String trailerUrl, LocalDateTime openDate, int runningTime, int episode, String rating,
            Map<String, AdminCategoryDTO> categories, Map<String, AdminPlatformDTO> platforms,
            List<Long> directors, List<Long> casts, List<String> countries) {
        return AdminContentRegisterJob.builder()
                .status(streamingStepStatus)
                .scheduledAt(getScheduledAt())
                .adminId(adminId)
                .title(title)
                .description(description)
                .posterUrl(posterUrl)
                .backdropUrl(backdropUrl)
                .trailerUrl(trailerUrl)
                .openDate(openDate)
                .runningTime(runningTime)
                .episode(episode)
                .rating(rating)
                .categories(categories)
                .platforms(platforms)
                .casts(casts)
                .directors(directors)
                .countries(countries)
                .build();
    }

    public void changeStatus(StreamingStatus status) {
        this.status = status;
    }

    public void setError(String errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public void setValidationErrors(List<JobValidationError> validationErrors) {
        this.validationErrors = validationErrors == null ? new ArrayList<>() : validationErrors;
    }

    public void clearErrors() {
        this.errorCode = null;
        this.errorMessage = null;
        this.validationErrors = new ArrayList<>();
    }

    public void resetRetryCount() {
        this.retryCount = 0;
    }

    public void updateFields(String title, String description, String posterUrl,
            String backdropUrl, String trailerUrl, LocalDateTime openDate,
            int runningTime, int episode, String rating,
            Map<String, AdminCategoryDTO> categories,
            Map<String, AdminPlatformDTO> platforms,
            List<Long> directors, List<Long> casts, List<String> countries) {
        this.title = title;
        this.description = description;
        this.posterUrl = posterUrl;
        this.backdropUrl = backdropUrl;
        this.trailerUrl = trailerUrl;
        this.openDate = openDate;
        this.runningTime = runningTime;
        this.episode = episode;
        this.rating = rating;
        this.categories = categories;
        this.platforms = platforms;
        this.directors = directors;
        this.casts = casts;
        this.countries = countries;
    }

    public void incrementRetryCount() {
        this.retryCount += 1;
    }

    public void incrementSkipCount() {
        this.skipCount += 1;
    }

    private static LocalDateTime getScheduledAt() {
        return TimeUtil.getScheduledAt();
    }

    public void finish() {
        finishedAt = LocalDateTime.now();
    }

    public void setStreamingJobMetricId(Long streamingJobMetricId) {
        this.streamingJobMetricId = streamingJobMetricId;
    }
}
