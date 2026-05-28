package com.example.udtbe.domain.scheduling.service;

import com.example.udtbe.domain.content.repository.CastRepository;
import com.example.udtbe.domain.content.repository.ContentCastRepository;
import com.example.udtbe.domain.content.repository.ContentCategoryRepository;
import com.example.udtbe.domain.content.repository.ContentCountryRepository;
import com.example.udtbe.domain.content.repository.ContentDirectorRepository;
import com.example.udtbe.domain.content.repository.ContentGenreRepository;
import com.example.udtbe.domain.content.repository.ContentMetadataRepository;
import com.example.udtbe.domain.content.repository.ContentPlatformRepository;
import com.example.udtbe.domain.content.repository.ContentRepository;
import com.example.udtbe.domain.content.repository.DirectorRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SoftDeleteCleanupService {

    public static final int RETENTION_DAYS = 30;

    private final ContentRepository contentRepository;
    private final ContentMetadataRepository contentMetadataRepository;
    private final ContentCastRepository contentCastRepository;
    private final ContentDirectorRepository contentDirectorRepository;
    private final ContentCategoryRepository contentCategoryRepository;
    private final ContentGenreRepository contentGenreRepository;
    private final ContentCountryRepository contentCountryRepository;
    private final ContentPlatformRepository contentPlatformRepository;
    private final CastRepository castRepository;
    private final DirectorRepository directorRepository;

    @Transactional
    public void cleanup() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(RETENTION_DAYS);

        int contentCastByContent = contentCastRepository.hardDeleteByContentSoftDeletedBefore(threshold);
        int contentDirectorByContent = contentDirectorRepository.hardDeleteByContentSoftDeletedBefore(threshold);
        int contentCategory = contentCategoryRepository.hardDeleteByContentSoftDeletedBefore(threshold);
        int contentGenre = contentGenreRepository.hardDeleteByContentSoftDeletedBefore(threshold);
        int contentCountry = contentCountryRepository.hardDeleteByContentSoftDeletedBefore(threshold);
        int contentPlatform = contentPlatformRepository.hardDeleteByContentSoftDeletedBefore(threshold);

        int contentMetadata = contentMetadataRepository.hardDeleteSoftDeletedBefore(threshold);
        int content = contentRepository.hardDeleteSoftDeletedBefore(threshold);

        int contentCastByCast = contentCastRepository.hardDeleteByCastSoftDeletedBefore(threshold);
        int contentDirectorByDirector = contentDirectorRepository.hardDeleteByDirectorSoftDeletedBefore(threshold);

        int cast = castRepository.hardDeleteSoftDeletedBefore(threshold);
        int director = directorRepository.hardDeleteSoftDeletedBefore(threshold);

        log.info("soft-delete cleanup 완료(threshold={}일): content={}, metadata={}, "
                        + "links_by_content(cast={}, director={}, category={}, genre={}, country={}, platform={}), "
                        + "links_by_actor(cast={}, director={}), "
                        + "cast={}, director={}",
                RETENTION_DAYS, content, contentMetadata,
                contentCastByContent, contentDirectorByContent, contentCategory, contentGenre, contentCountry, contentPlatform,
                contentCastByCast, contentDirectorByDirector,
                cast, director);
    }
}
