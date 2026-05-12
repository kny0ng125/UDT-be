package com.example.udtbe.batch.scheduler;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

import com.example.udtbe.domain.batch.dto.IntegrityCheckResult;
import com.example.udtbe.domain.batch.scheduler.AdminScheduler;
import com.example.udtbe.domain.batch.service.IntegrityCheckService;
import com.example.udtbe.domain.content.service.LuceneIndexService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminSchedulerTest {

    @Mock
    private IntegrityCheckService integrityCheckService;

    @Mock
    private LuceneIndexService luceneIndexService;

    @InjectMocks
    private AdminScheduler adminScheduler;

    @DisplayName("runIntegrityCheck: 새벽 4시 무결성 체크를 실행한다")
    @Test
    void runIntegrityCheck_invokesService() {
        // given
        IntegrityCheckResult result = new IntegrityCheckResult(10, 0, 0, 0, false);
        given(integrityCheckService.runIntegrityCheck()).willReturn(result);

        // when
        adminScheduler.runIntegrityCheck();

        // then
        verify(integrityCheckService).runIntegrityCheck();
    }

    @DisplayName("runIntegrityCheck: 전체 리빌드가 트리거되어도 정상 종료된다")
    @Test
    void runIntegrityCheck_withFullRebuild() {
        // given
        IntegrityCheckResult result = new IntegrityCheckResult(10, 5, 4, 9, true);
        given(integrityCheckService.runIntegrityCheck()).willReturn(result);

        // when
        adminScheduler.runIntegrityCheck();

        // then
        verify(integrityCheckService).runIntegrityCheck();
    }

    @DisplayName("rebuildLuceneIndexWithRetry: 인덱스 리빌드 메서드를 호출한다")
    @Test
    void rebuildLuceneIndexWithRetry_success() {
        // given
        doNothing().when(luceneIndexService).buildIndexOnStartup();

        // when
        adminScheduler.rebuildLuceneIndexWithRetry();

        // then
        verify(luceneIndexService).buildIndexOnStartup();
    }
}
