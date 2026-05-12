package com.example.udtbe.domain.batch.scheduler;

import com.example.udtbe.domain.batch.dto.IntegrityCheckResult;
import com.example.udtbe.domain.batch.service.IntegrityCheckService;
import com.example.udtbe.domain.content.service.LuceneIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminScheduler {

    private final IntegrityCheckService integrityCheckService;
    private final LuceneIndexService luceneIndexService;

    @Scheduled(cron = "0 0 4 * * *")
    @Retryable(retryFor = Exception.class, backoff = @Backoff(delay = 5000))
    public void runIntegrityCheck() {
        log.info("새벽 4시 무결성 체크 시작");
        IntegrityCheckResult result = integrityCheckService.runIntegrityCheck();
        log.info("무결성 체크 완료: 전체={}, 누락={}, 고아={}, 수정={}, 전체리빌드={}",
                result.totalChecked(), result.missingInIndex(),
                result.orphanedInIndex(), result.fixed(), result.fullRebuildTriggered());
    }

    @Retryable(retryFor = Exception.class, backoff = @Backoff(delay = 5000))
    public void rebuildLuceneIndexWithRetry() {
        luceneIndexService.buildIndexOnStartup();
    }
}
