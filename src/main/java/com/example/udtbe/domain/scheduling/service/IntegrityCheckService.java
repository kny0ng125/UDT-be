package com.example.udtbe.domain.scheduling.service;

import com.example.udtbe.domain.scheduling.dto.IntegrityCheckResult;
import com.example.udtbe.domain.content.entity.ContentMetadata;
import com.example.udtbe.domain.content.repository.ContentMetadataRepository;
import com.example.udtbe.domain.content.service.LuceneIndexService;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrityCheckService {

    private static final int FULL_REBUILD_THRESHOLD_PERCENT = 10;

    private final ContentMetadataRepository contentMetadataRepository;
    private final LuceneIndexService luceneIndexService;

    @Transactional(readOnly = true)
    public IntegrityCheckResult runIntegrityCheck() {
        log.info("===== DB-Lucene 무결성 체크 시작 =====");

        List<ContentMetadata> dbMetadataList = contentMetadataRepository.findByIsDeletedFalse();
        Set<Long> dbContentIds = dbMetadataList.stream()
                .map(m -> m.getContent().getId())
                .collect(Collectors.toSet());

        Map<Long, ContentMetadata> dbMetadataMap = dbMetadataList.stream()
                .collect(Collectors.toMap(
                        m -> m.getContent().getId(),
                        Function.identity()));

        Set<Long> indexedIds = luceneIndexService.getAllIndexedContentIds();

        Set<Long> missingInIndex = new HashSet<>(dbContentIds);
        missingInIndex.removeAll(indexedIds);

        Set<Long> orphanedInIndex = new HashSet<>(indexedIds);
        orphanedInIndex.removeAll(dbContentIds);

        int total = dbContentIds.size();
        int mismatchCount = missingInIndex.size() + orphanedInIndex.size();

        log.info("무결성 체크: DB={}건, 인덱스={}건, 누락={}건, 고아={}건",
                total, indexedIds.size(), missingInIndex.size(), orphanedInIndex.size());

        if (total > 0 && (mismatchCount * 100 / total) > FULL_REBUILD_THRESHOLD_PERCENT) {
            log.warn("불일치 비율 {}% 초과 - 전체 리빌드 실행", FULL_REBUILD_THRESHOLD_PERCENT);
            try {
                luceneIndexService.rebuildIndex();
            } catch (IOException e) {
                log.error("전체 리빌드 실패: {}", e.getMessage());
            }
            return new IntegrityCheckResult(total, missingInIndex.size(),
                    orphanedInIndex.size(), mismatchCount, true);
        }

        int fixed = 0;
        for (Long id : missingInIndex) {
            ContentMetadata metadata = dbMetadataMap.get(id);
            if (metadata != null) {
                luceneIndexService.addDocument(metadata);
                fixed++;
            }
        }
        for (Long id : orphanedInIndex) {
            luceneIndexService.deleteDocument(id);
            fixed++;
        }

        log.info("===== 무결성 체크 완료: 수정 {}건 =====", fixed);
        return new IntegrityCheckResult(total, missingInIndex.size(),
                orphanedInIndex.size(), fixed, false);
    }
}
