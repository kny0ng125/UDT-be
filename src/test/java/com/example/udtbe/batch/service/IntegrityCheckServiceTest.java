package com.example.udtbe.batch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.udtbe.common.fixture.ContentFixture;
import com.example.udtbe.common.fixture.ContentMetadataFixture;
import com.example.udtbe.domain.batch.dto.IntegrityCheckResult;
import com.example.udtbe.domain.batch.service.IntegrityCheckService;
import com.example.udtbe.domain.content.entity.ContentMetadata;
import com.example.udtbe.domain.content.repository.ContentMetadataRepository;
import com.example.udtbe.domain.content.service.LuceneIndexService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IntegrityCheckServiceTest {

    @Mock
    private ContentMetadataRepository contentMetadataRepository;

    @Mock
    private LuceneIndexService luceneIndexService;

    @InjectMocks
    private IntegrityCheckService integrityCheckService;

    @DisplayName("DB와 인덱스가 완전 일치하면 수정 없이 결과를 반환한다")
    @Test
    void runCheck_consistent() {
        // given
        ContentMetadata m1 = ContentMetadataFixture.metadata(
                ContentFixture.parasite(), "NETFLIX", "ACTION");
        given(contentMetadataRepository.findByIsDeletedFalse()).willReturn(List.of(m1));
        given(luceneIndexService.getAllIndexedContentIds()).willReturn(Set.of(1L));

        // when
        IntegrityCheckResult result = integrityCheckService.runIntegrityCheck();

        // then
        assertThat(result.totalChecked()).isEqualTo(1);
        assertThat(result.missingInIndex()).isZero();
        assertThat(result.orphanedInIndex()).isZero();
        assertThat(result.fixed()).isZero();
        assertThat(result.fullRebuildTriggered()).isFalse();
        verify(luceneIndexService, never()).addDocument(any());
        verify(luceneIndexService, never()).deleteDocument(anyLong());
    }

    @DisplayName("인덱스에 누락된 컨텐츠는 addDocument로 보정한다")
    @Test
    void runCheck_addsMissing() {
        // given (DB 10건 / 인덱스 9건: oldboy(id=2) 누락 → 10% 불일치, 임계치 미만)
        List<ContentMetadata> dbList = ContentFixture.allTestMovies().stream()
                .map(c -> ContentMetadataFixture.metadata(c, "NETFLIX", "ACTION"))
                .toList();
        Set<Long> indexed = new HashSet<>(dbList.stream()
                .map(m -> m.getContent().getId())
                .collect(Collectors.toSet()));
        indexed.remove(2L);

        given(contentMetadataRepository.findByIsDeletedFalse()).willReturn(dbList);
        given(luceneIndexService.getAllIndexedContentIds()).willReturn(indexed);

        // when
        IntegrityCheckResult result = integrityCheckService.runIntegrityCheck();

        // then
        assertThat(result.totalChecked()).isEqualTo(10);
        assertThat(result.missingInIndex()).isEqualTo(1);
        assertThat(result.fixed()).isEqualTo(1);
        assertThat(result.fullRebuildTriggered()).isFalse();
        verify(luceneIndexService, times(1)).addDocument(any());
    }

    @DisplayName("인덱스에만 있는 고아 컨텐츠는 deleteDocument로 제거한다")
    @Test
    void runCheck_removesOrphans() {
        // given (10건 DB, 인덱스에 11건 → 고아 1건 = 9% 미만)
        List<ContentMetadata> dbList = ContentFixture.allTestMovies().stream()
                .map(c -> ContentMetadataFixture.metadata(c, "NETFLIX", "ACTION"))
                .toList();
        Set<Long> indexed = new HashSet<>(dbList.stream()
                .map(m -> m.getContent().getId())
                .collect(Collectors.toSet()));
        indexed.add(999L);

        given(contentMetadataRepository.findByIsDeletedFalse()).willReturn(dbList);
        given(luceneIndexService.getAllIndexedContentIds()).willReturn(indexed);

        // when
        IntegrityCheckResult result = integrityCheckService.runIntegrityCheck();

        // then
        assertThat(result.orphanedInIndex()).isEqualTo(1);
        assertThat(result.fixed()).isEqualTo(1);
        assertThat(result.fullRebuildTriggered()).isFalse();
        verify(luceneIndexService).deleteDocument(999L);
    }

    @DisplayName("불일치 비율이 10% 초과면 전체 리빌드를 실행하고 증분 보정은 건너뛴다")
    @Test
    void runCheck_triggerFullRebuild() throws Exception {
        // given (DB 10건, 인덱스에 1건만 → 90% 불일치)
        List<ContentMetadata> dbList = ContentFixture.allTestMovies().stream()
                .map(c -> ContentMetadataFixture.metadata(c, "NETFLIX", "ACTION"))
                .toList();
        given(contentMetadataRepository.findByIsDeletedFalse()).willReturn(dbList);
        given(luceneIndexService.getAllIndexedContentIds()).willReturn(Set.of(1L));

        // when
        IntegrityCheckResult result = integrityCheckService.runIntegrityCheck();

        // then
        assertThat(result.fullRebuildTriggered()).isTrue();
        assertThat(result.missingInIndex()).isEqualTo(9);
        verify(luceneIndexService).rebuildIndex();
        verify(luceneIndexService, never()).addDocument(any());
        verify(luceneIndexService, never()).deleteDocument(anyLong());
    }

}
