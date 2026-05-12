package com.example.udtbe.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.example.udtbe.common.fixture.ContentFixture;
import com.example.udtbe.common.fixture.ContentMetadataFixture;
import com.example.udtbe.domain.content.entity.Content;
import com.example.udtbe.domain.content.entity.ContentMetadata;
import com.example.udtbe.domain.content.repository.ContentMetadataRepository;
import com.example.udtbe.domain.content.service.LuceneIndexService;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.ko.KoreanAnalyzer;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class LuceneIndexServiceTest {

    @Mock
    private ContentMetadataRepository contentMetadataRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private Analyzer analyzer;
    private Directory directory;
    private LuceneIndexService luceneIndexService;

    @BeforeEach
    void setUp() throws IOException {
        analyzer = new KoreanAnalyzer();
        directory = new ByteBuffersDirectory();
        luceneIndexService = new LuceneIndexService(
                contentMetadataRepository, analyzer, directory, eventPublisher);

        given(contentMetadataRepository.findByIsDeletedFalse()).willReturn(List.of());
        luceneIndexService.rebuildIndex();
    }

    @AfterEach
    void tearDown() throws IOException {
        directory.close();
        analyzer.close();
    }

    @DisplayName("addDocument: 단건 추가 시 인덱스에 contentId가 포함된다")
    @Test
    void addDocument_success() {
        // given
        Content content = ContentFixture.parasite();
        ContentMetadata metadata = ContentMetadataFixture.metadata(content, "NETFLIX", "ACTION");

        // when
        luceneIndexService.addDocument(metadata);

        // then
        Set<Long> ids = luceneIndexService.getAllIndexedContentIds();
        assertThat(ids).containsExactly(1L);
    }

    @DisplayName("updateDocument: 동일 contentId의 기존 문서를 새 문서로 대체한다")
    @Test
    void updateDocument_replacesDocument() {
        // given
        Content content = ContentFixture.parasite();
        luceneIndexService.addDocument(
                ContentMetadataFixture.metadata(content, "NETFLIX", "ACTION"));

        ContentMetadata updated = ContentMetadataFixture.metadata(content, "WATCHA", "COMEDY");

        // when
        luceneIndexService.updateDocument(1L, updated);

        // then
        Set<Long> ids = luceneIndexService.getAllIndexedContentIds();
        assertThat(ids).hasSize(1).containsExactly(1L);
    }

    @DisplayName("deleteDocument: 단건 삭제 시 인덱스에서 contentId가 제거된다")
    @Test
    void deleteDocument_success() {
        // given
        Content content = ContentFixture.parasite();
        luceneIndexService.addDocument(
                ContentMetadataFixture.metadata(content, "NETFLIX", "ACTION"));

        // when
        luceneIndexService.deleteDocument(1L);

        // then
        Set<Long> ids = luceneIndexService.getAllIndexedContentIds();
        assertThat(ids).doesNotContain(1L);
    }

    @DisplayName("getAllIndexedContentIds: 인덱싱된 모든 contentId를 반환한다")
    @Test
    void getAllIndexedContentIds_returnsAll() {
        // given
        Content c1 = ContentFixture.parasite();
        Content c2 = ContentFixture.oldboy();
        luceneIndexService.addDocument(
                ContentMetadataFixture.metadata(c1, "NETFLIX", "ACTION"));
        luceneIndexService.addDocument(
                ContentMetadataFixture.metadata(c2, "WATCHA", "DRAMA"));

        // when
        Set<Long> ids = luceneIndexService.getAllIndexedContentIds();

        // then
        assertThat(ids).containsExactlyInAnyOrder(1L, 2L);
    }

    @DisplayName("rebuildIndex: 전체 인덱스를 재구축하고 성공 건수를 반환한다")
    @Test
    void rebuildIndex_returnsSuccessCount() throws IOException {
        // given
        List<ContentMetadata> metadataList = List.of(
                ContentMetadataFixture.metadata(ContentFixture.parasite(), "NETFLIX", "ACTION"),
                ContentMetadataFixture.metadata(ContentFixture.oldboy(), "WATCHA", "DRAMA")
        );
        given(contentMetadataRepository.findByIsDeletedFalse()).willReturn(metadataList);

        // when
        int success = luceneIndexService.rebuildIndex();

        // then
        assertThat(success).isEqualTo(2);
        assertThat(luceneIndexService.getAllIndexedContentIds())
                .containsExactlyInAnyOrder(1L, 2L);
        assertThat(luceneIndexService.isIndexBuilt()).isTrue();
    }

    @DisplayName("rebuildIndex: 기존 인덱스를 모두 삭제한 뒤 새로 구축한다")
    @Test
    void rebuildIndex_clearsPreviousIndex() throws IOException {
        // given
        luceneIndexService.addDocument(
                ContentMetadataFixture.metadata(ContentFixture.parasite(), "NETFLIX", "ACTION"));
        luceneIndexService.addDocument(
                ContentMetadataFixture.metadata(ContentFixture.oldboy(), "WATCHA", "DRAMA"));

        given(contentMetadataRepository.findByIsDeletedFalse()).willReturn(List.of(
                ContentMetadataFixture.metadata(ContentFixture.interstellar(), "NETFLIX", "SF")
        ));

        // when
        luceneIndexService.rebuildIndex();

        // then
        assertThat(luceneIndexService.getAllIndexedContentIds())
                .containsExactly(3L);
    }
}
