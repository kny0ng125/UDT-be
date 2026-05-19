package com.example.udtbe.domain.content.service;

import com.example.udtbe.domain.content.entity.ContentMetadata;
import com.example.udtbe.domain.content.event.IndexRebuildCompleteEvent;
import com.example.udtbe.domain.content.exception.RecommendContentErrorCode;
import com.example.udtbe.domain.content.repository.ContentMetadataRepository;
import com.example.udtbe.global.exception.RestApiException;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LuceneIndexService {

    private final ContentMetadataRepository contentMetadataRepository;
    private final Analyzer analyzer;
    private final Directory directory;
    private final ApplicationEventPublisher eventPublisher;

    private final ReentrantReadWriteLock indexLock = new ReentrantReadWriteLock();
    private boolean indexBuilt = false;

    @EventListener(ApplicationReadyEvent.class)
    public void buildIndexOnStartup() {
        log.info("===== Lucene 인덱스 초기화 시작 =====");
        try {
            rebuildIndex();
        } catch (Exception e) {
            throw new RestApiException(RecommendContentErrorCode.LUCENE_INDEX_NOT_BUILT);
        }
    }

    public int rebuildIndex() throws IOException {
        indexLock.writeLock().lock();
        long startTime = System.currentTimeMillis();
        try {
            List<ContentMetadata> contentMetadataList = contentMetadataRepository.findByIsDeletedFalse();
            log.info("인덱싱 대상 ContentMetadata: {}개", contentMetadataList.size());

            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            try (IndexWriter indexWriter = new IndexWriter(directory, config)) {
                indexWriter.deleteAll();

                int successCount = 0;
                for (ContentMetadata metadata : contentMetadataList) {
                    try {
                        Document doc = createDocument(metadata);
                        indexWriter.addDocument(doc);
                        successCount++;
                    } catch (Exception e) {
                        log.warn("문서 인덱싱 실패 - contentId={}: {}",
                                metadata.getContent().getId(), e.getMessage());
                    }
                }

                indexWriter.commit();
                indexBuilt = true;

                long buildTime = System.currentTimeMillis() - startTime;
                log.info("인덱싱 완료: {}/{}개 성공 ({}ms)",
                        successCount, contentMetadataList.size(), buildTime);

                // 전체 리빌드 완료 → 추천 캐시 무효화 (기동/무결성 공통 경로)
                eventPublisher.publishEvent(
                        IndexRebuildCompleteEvent.of(this, successCount, buildTime));

                return successCount;
            }
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    public void addDocument(ContentMetadata metadata) {
        indexLock.writeLock().lock();
        try {
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(OpenMode.APPEND);
            try (IndexWriter writer = new IndexWriter(directory, config)) {
                writer.addDocument(createDocument(metadata));
                writer.commit();
                log.info("Lucene 문서 추가 완료 - contentId={}", metadata.getContent().getId());
            }
        } catch (IOException e) {
            log.warn("Lucene 문서 추가 실패 - contentId={}: {}",
                    metadata.getContent().getId(), e.getMessage());
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    public void updateDocument(Long contentId, ContentMetadata metadata) {
        indexLock.writeLock().lock();
        try {
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(OpenMode.APPEND);
            try (IndexWriter writer = new IndexWriter(directory, config)) {
                writer.updateDocument(
                        new Term("contentId", contentId.toString()),
                        createDocument(metadata));
                writer.commit();
                log.info("Lucene 문서 수정 완료 - contentId={}", contentId);
            }
        } catch (IOException e) {
            log.warn("Lucene 문서 수정 실패 - contentId={}: {}", contentId, e.getMessage());
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    public void deleteDocument(Long contentId) {
        indexLock.writeLock().lock();
        try {
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(OpenMode.APPEND);
            try (IndexWriter writer = new IndexWriter(directory, config)) {
                writer.deleteDocuments(new Term("contentId", contentId.toString()));
                writer.commit();
                log.info("Lucene 문서 삭제 완료 - contentId={}", contentId);
            }
        } catch (IOException e) {
            log.warn("Lucene 문서 삭제 실패 - contentId={}: {}", contentId, e.getMessage());
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    public Set<Long> getAllIndexedContentIds() {
        Set<Long> contentIds = new HashSet<>();
        indexLock.readLock().lock();
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            StoredFields storedFields = reader.storedFields();
            for (int i = 0; i < reader.maxDoc(); i++) {
                Document doc = storedFields.document(i);
                String idStr = doc.get("contentId");
                if (idStr != null) {
                    contentIds.add(Long.parseLong(idStr));
                }
            }
        } catch (IOException e) {
            log.warn("Lucene 인덱스 contentId 조회 실패: {}", e.getMessage());
        } finally {
            indexLock.readLock().unlock();
        }
        return contentIds;
    }

    private Document createDocument(ContentMetadata metadata) {
        Document doc = new Document();
        Long contentId = metadata.getContent().getId();

        doc.add(new LongPoint("contentId", contentId));
        doc.add(new StringField("contentId", contentId.toString(), Field.Store.YES));
        doc.add(new TextField("title", metadata.getTitle(), Field.Store.YES));

        String platformTag = metadata.getPlatformTag() != null ?
                String.join(",", metadata.getPlatformTag()) : "";
        String genreTag = metadata.getGenreTag() != null ?
                String.join(",", metadata.getGenreTag()) : "";
//        String directorTag = metadata.getDirectorTag() != null ?
//                String.join(",", metadata.getDirectorTag()) : "";
        String rating = metadata.getRating() != null ? metadata.getRating() : "";

        doc.add(new TextField("platformTag", platformTag, Field.Store.YES));
        doc.add(new TextField("genreTag", genreTag, Field.Store.YES));
//        doc.add(new TextField("directorTag", directorTag, Field.Store.YES));

        log.trace("문서 생성: contentId={}, title='{}', platforms='{}', genres='{}', rating='{}'",
                contentId, metadata.getTitle(), platformTag, genreTag, rating);

        return doc;
    }

    public DirectoryReader getIndexReader() throws IOException {
        if (!indexBuilt) {
            throw new IllegalStateException("인덱스가 아직 빌드되지 않았습니다");
        }
        return DirectoryReader.open(directory);
    }

    public Analyzer getAnalyzer() {
        log.trace("Analyzer 반환: {}", analyzer.getClass().getSimpleName());
        return analyzer;
    }

    public boolean isIndexBuilt() {
        log.trace("인덱스 빌드 상태 확인: {}", indexBuilt);
        return indexBuilt;
    }
}