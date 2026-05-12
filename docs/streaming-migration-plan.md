# 배치 -> 스트리밍 전환 구현 계획

## 1. 개요

### 1.1 현재 구조 (AS-IS)

```
Admin API 호출
    |
    v
Job 테이블에 PENDING 저장 (AdminContentRegisterJob / UpdateJob / DeleteJob)
    |
    v  (대기)
매일 새벽 4시 - AdminScheduler
    |
    v
Spring Batch contentBatchJob 실행
    ├── Step 1: contentRegisterStep (등록)
    ├── Step 2: contentUpdateStep   (수정)
    └── Step 3: contentDeleteStep   (삭제)
    |
    v
Lucene 인덱스 전체 리빌드
    |
    v
추천 캐시 무효화
```

### 1.2 전환 구조 (TO-BE)

```
Admin API 호출
    |
    v
즉시 처리 (DB 등록/수정/삭제) + Job 엔티티로 추적 기록
    |
    v  (트랜잭션 커밋 후)
ContentStreamingEvent 발행
    |
    v
Lucene 증분 인덱싱 (단건 add/update/delete)
    |
    v
추천 캐시 무효화

---

매일 새벽 4시 - AdminScheduler
    |
    v
IntegrityCheckService: DB <-> Lucene 무결성 체크만 수행
```

### 1.3 핵심 변경 포인트

| 항목 | AS-IS | TO-BE |
|------|-------|-------|
| 컨텐츠 처리 시점 | 새벽 4시 일괄 처리 | API 호출 즉시 처리 |
| Lucene 업데이트 | 전체 리빌드 | 증분 (단건 add/update/delete) |
| 새벽 4시 스케줄러 | 배치 Job 실행 | DB-Lucene 무결성 체크 |
| 추적 방식 | Job 엔티티 (PENDING -> 배치 처리) | Job 엔티티 (PROCESSING -> 즉시 완료) |
| 에러 복구 | 배치 재시도 (3회) | API 에러 응답 + 새벽 무결성 보정 |

---

## 2. 상세 설계

### Phase 1: LuceneIndexService 증분 인덱싱 추가

**대상 파일**: `src/main/java/com/example/udtbe/domain/content/service/LuceneIndexService.java`

#### 현재 상태
- `buildIndex()` : 전체 삭제(`deleteAll`) 후 재구축만 존재
- `createDocument(ContentMetadata)` : Document 생성 (private)
- `getIndexReader()` : 매번 `DirectoryReader.open(directory)` 호출

#### 변경 사항

```java
// 1. 동시 쓰기 방지용 Lock 추가
private final ReentrantReadWriteLock indexLock = new ReentrantReadWriteLock();

// 2. 단건 추가
public void addDocument(ContentMetadata metadata) {
    indexLock.writeLock().lock();
    try {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.APPEND);
        try (IndexWriter writer = new IndexWriter(directory, config)) {
            writer.addDocument(createDocument(metadata));
            writer.commit();
        }
    } finally {
        indexLock.writeLock().unlock();
    }
}

// 3. 단건 수정 (기존 문서 삭제 후 새 문서 추가 - 원자적)
public void updateDocument(Long contentId, ContentMetadata metadata) {
    indexLock.writeLock().lock();
    try {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.APPEND);
        try (IndexWriter writer = new IndexWriter(directory, config)) {
            writer.updateDocument(
                new Term("contentId", contentId.toString()),
                createDocument(metadata)
            );
            writer.commit();
        }
    } finally {
        indexLock.writeLock().unlock();
    }
}

// 4. 단건 삭제
public void deleteDocument(Long contentId) {
    indexLock.writeLock().lock();
    try {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.APPEND);
        try (IndexWriter writer = new IndexWriter(directory, config)) {
            writer.deleteDocuments(new Term("contentId", contentId.toString()));
            writer.commit();
        }
    } finally {
        indexLock.writeLock().unlock();
    }
}

// 5. 무결성 체크용 - 인덱싱된 전체 contentId 조회
public Set<Long> getAllIndexedContentIds() {
    // DirectoryReader로 모든 문서 순회하여 contentId 수집
}

// 6. buildIndex() -> rebuildIndex()로 rename + public 전환
public int rebuildIndex() throws IOException { ... }
```

#### 설계 근거

- **스레드 안전**: `ByteBuffersDirectory`는 싱글톤 빈. `ReentrantReadWriteLock`으로 동시 쓰기 방지
- **Reader 가시성**: `getIndexReader()`가 매 호출마다 `DirectoryReader.open()` → commit 즉시 검색 반영
- **성능**: Admin 작업 빈도(분 단위)에서 lock 직렬화는 문제없음

---

### Phase 2: 컨텐츠 스트리밍 이벤트 시스템

#### 신규 파일

**`ContentStreamingType.java`** (enum)
```
src/main/java/com/example/udtbe/domain/content/event/ContentStreamingType.java
```
```java
public enum ContentStreamingType {
    REGISTER, UPDATE, DELETE
}
```

**`ContentStreamingEvent.java`** (이벤트)
```
src/main/java/com/example/udtbe/domain/content/event/ContentStreamingEvent.java
```
```java
public class ContentStreamingEvent extends ApplicationEvent {
    private final ContentStreamingType type;
    private final Long contentId;
    private final ContentMetadata metadata; // DELETE 시 null
}
```

**`ContentStreamingEventListener.java`** (리스너)
```
src/main/java/com/example/udtbe/domain/content/event/ContentStreamingEventListener.java
```
```java
@Component
public class ContentStreamingEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleContentStreaming(ContentStreamingEvent event) {
        switch (event.getType()) {
            case REGISTER -> luceneIndexService.addDocument(event.getMetadata());
            case UPDATE   -> luceneIndexService.updateDocument(event.getContentId(), event.getMetadata());
            case DELETE   -> luceneIndexService.deleteDocument(event.getContentId());
        }
    }
}
```

#### 설계 근거

- **`@TransactionalEventListener(AFTER_COMMIT)`**: DB 트랜잭션 커밋 후에만 Lucene 업데이트 실행
  - 트랜잭션 롤백 시 Lucene 이벤트 미발행 → 일관성 보장
- **실패 시 WARN 로깅만**: DB가 source of truth. Lucene 업데이트 실패는 새벽 무결성 체크가 보정
- 기존 `FeedbackStatEvent` 패턴(`@TransactionalEventListener + AFTER_COMMIT`)과 동일한 설계

---

### Phase 3: AdminService 스트리밍 전환 (핵심)

**대상 파일**: `src/main/java/com/example/udtbe/domain/admin/service/AdminService.java`

#### registerContent() 리턴타입 변경

```java
// AS-IS
public void registerContent(AdminContentRegisterRequest request) { ... }

// TO-BE - Content 반환하도록 변경
public Content registerContent(AdminContentRegisterRequest request) {
    Content content = contentRepository.save(AdminContentMapper.toContentEntity(request));
    // ... 기존 로직 동일 ...
    return content;
}
```

#### registerBulkContent() 변경

```java
// AS-IS: Job 생성만 하고 반환
@Transactional
public AdminContentRegisterResponse registerBulkContent(Admin admin,
        AdminContentRegisterRequest request) {
    AdminContentRegisterJob job = AdminContentMapper.toContentRegisterJob(request, admin.getId());
    adminContentRegisterJobRepository.save(job);
    return AdminContentMapper.toContentRegisterResponse(job.getId());
}

// TO-BE: 즉시 처리 + 추적 기록
@Transactional
public AdminContentRegisterResponse registerBulkContent(Admin admin,
        AdminContentRegisterRequest request) {
    // 1. 추적 Job 생성 (PROCESSING)
    AdminContentRegisterJob job = AdminContentMapper.toContentRegisterJob(request, admin.getId());
    job.changeStatus(BatchStatus.PROCESSING);
    adminContentRegisterJobRepository.save(job);

    try {
        // 2. 유효성 검증
        adminQuery.validRegisterAndUpdateContent(
            request.categories(), request.platforms(), request.casts(), request.directors());

        // 3. 즉시 컨텐츠 등록
        Content content = registerContent(request);

        // 4. Lucene 증분 업데이트 이벤트 발행
        ContentMetadata metadata = adminQuery.findContentMetadataByContentId(content.getId());
        eventPublisher.publishEvent(
            ContentStreamingEvent.of(this, ContentStreamingType.REGISTER,
                content.getId(), metadata));

        // 5. 완료 처리
        job.changeStatus(BatchStatus.COMPLETED);
        job.finish();

    } catch (RestApiException e) {
        job.changeStatus(BatchStatus.INVALID);
        job.setError("VALIDATION_ERROR", e.getMessage());
        job.finish();
        throw e;
    } catch (Exception e) {
        job.changeStatus(BatchStatus.FAILED);
        job.setError("PROCESSING_ERROR", e.getMessage());
        job.finish();
        throw e;
    }

    return AdminContentMapper.toContentRegisterResponse(job.getId());
}
```

#### updateBulkContent() 변경 (동일 패턴)

```java
@Transactional
public AdminContentUpdateResponse updateBulkContent(Admin admin, Long contentId,
        AdminContentUpdateRequest request) {
    AdminContentUpdateJob job = AdminContentMapper.toContentUpdateJob(request, contentId, admin.getId());
    job.changeStatus(BatchStatus.PROCESSING);
    adminContentUpdateJobRepository.save(job);

    try {
        adminQuery.validContentByContentId(contentId);
        adminQuery.validRegisterAndUpdateContent(
            request.categories(), request.platforms(), request.casts(), request.directors());

        updateContent(contentId, request);

        ContentMetadata metadata = adminQuery.findContentMetadataByContentId(contentId);
        eventPublisher.publishEvent(
            ContentStreamingEvent.of(this, ContentStreamingType.UPDATE, contentId, metadata));

        job.changeStatus(BatchStatus.COMPLETED);
        job.finish();
    } catch (RestApiException e) {
        job.changeStatus(BatchStatus.INVALID);
        job.setError("VALIDATION_ERROR", e.getMessage());
        job.finish();
        throw e;
    } catch (Exception e) {
        job.changeStatus(BatchStatus.FAILED);
        job.setError("PROCESSING_ERROR", e.getMessage());
        job.finish();
        throw e;
    }

    return AdminContentMapper.toContentUpdateResponse(job.getId());
}
```

#### deleteBulkContent() 변경

```java
@Transactional
public AdminContentDeleteResponse deleteBulkContent(Admin admin, Long contentId) {
    AdminContentDeleteJob job = AdminContentMapper.toContentDeleteJob(contentId, admin.getId());
    job.changeStatus(BatchStatus.PROCESSING);
    adminContentDeleteJobRepository.save(job);

    try {
        deleteContent(contentId);

        eventPublisher.publishEvent(
            ContentStreamingEvent.of(this, ContentStreamingType.DELETE, contentId, null));

        job.changeStatus(BatchStatus.COMPLETED);
        job.finish();
    } catch (RestApiException e) {
        job.changeStatus(BatchStatus.INVALID);
        job.setError("VALIDATION_ERROR", e.getMessage());
        job.finish();
        throw e;
    } catch (Exception e) {
        job.changeStatus(BatchStatus.FAILED);
        job.setError("PROCESSING_ERROR", e.getMessage());
        job.finish();
        throw e;
    }

    return AdminContentMapper.toContentDeleteResponse(job.getId());
}
```

---

### Phase 4: 스트리밍 추적 데이터 정비

#### 기존 Job 엔티티 재활용

기존 `AdminContentRegisterJob`, `AdminContentUpdateJob`, `AdminContentDeleteJob` 엔티티를 **그대로 사용**.

| 필드 | AS-IS (배치) | TO-BE (스트리밍) |
|------|-------------|-----------------|
| `status` | PENDING -> (4시) -> PROCESSING -> COMPLETED | PROCESSING -> COMPLETED (즉시) |
| `scheduledAt` | 다음 새벽 4시 | 현재 시각 (`LocalDateTime.now()`) |
| `finishedAt` | 배치 처리 완료 시각 | API 처리 완료 시각 (거의 즉시) |
| `errorCode` | 배치 에러 코드 | 스트리밍 에러 코드 |
| `batchJobMetricId` | BatchJobMetric FK | null (건별 처리이므로 불필요) |

#### TimeUtil 수정

**대상 파일**: `src/main/java/com/example/udtbe/domain/batch/util/TimeUtil.java`

```java
// AS-IS: 다음 새벽 4시 반환
public static LocalDateTime getScheduledAt() {
    // 4시 기준 계산 로직
}

// TO-BE: 현재 시각 반환
public static LocalDateTime getScheduledAt() {
    return LocalDateTime.now();
}
```

#### 기존 API 호환성

- `GET /api/admin/batch` : Job 엔티티 조회 -> 동일하게 작동 (스트리밍 결과도 포함)
- `GET /api/admin/batch/results` : BatchJobMetric 기반 -> 기존 메트릭은 유지
- `GET /api/admin/batch/metrics` : 집계 로직 -> Job status 기반이므로 호환

---

### Phase 5: 새벽 4시 무결성 체크

#### 신규 파일

**`IntegrityCheckResult.java`** (record)
```
src/main/java/com/example/udtbe/domain/batch/dto/IntegrityCheckResult.java
```
```java
public record IntegrityCheckResult(
    int totalChecked,
    int missingInIndex,
    int orphanedInIndex,
    int fixed,
    boolean fullRebuildTriggered
) {}
```

**`IntegrityCheckService.java`** (서비스)
```
src/main/java/com/example/udtbe/domain/batch/service/IntegrityCheckService.java
```
```java
@Service
public class IntegrityCheckService {

    public IntegrityCheckResult runIntegrityCheck() {
        // 1. DB에서 isDeleted=false인 ContentMetadata의 contentId Set 조회
        Set<Long> dbContentIds = contentMetadataRepository.findByIsDeletedFalse()
            .stream()
            .map(m -> m.getContent().getId())
            .collect(Collectors.toSet());

        // 2. Lucene 인덱스에서 전체 contentId Set 조회
        Set<Long> indexedIds = luceneIndexService.getAllIndexedContentIds();

        // 3. 차집합 계산
        Set<Long> missingInIndex = difference(dbContentIds, indexedIds);   // DB에만 있음
        Set<Long> orphanedInIndex = difference(indexedIds, dbContentIds);  // Lucene에만 있음

        // 4. 불일치 비율 판단
        int total = dbContentIds.size();
        int mismatchCount = missingInIndex.size() + orphanedInIndex.size();

        if (total > 0 && (mismatchCount * 100 / total) > 10) {
            // 10% 초과 불일치 -> 전체 리빌드
            luceneIndexService.rebuildIndex();
            return new IntegrityCheckResult(total, missingInIndex.size(),
                orphanedInIndex.size(), mismatchCount, true);
        }

        // 5. 증분 보정
        missingInIndex.forEach(id -> {
            ContentMetadata metadata = findMetadataByContentId(id);
            luceneIndexService.addDocument(metadata);
        });
        orphanedInIndex.forEach(id -> luceneIndexService.deleteDocument(id));

        return new IntegrityCheckResult(total, missingInIndex.size(),
            orphanedInIndex.size(), mismatchCount, false);
    }
}
```

#### AdminScheduler 변경

**대상 파일**: `src/main/java/com/example/udtbe/domain/batch/scheduler/AdminScheduler.java`

```java
// AS-IS
@Scheduled(cron = "0 0 4 * * *")
public void runContentBatchJob() {
    jobLauncher.run(contentBatchJob, jobParameters);
    adminService.allUpdateMetric();
}

// TO-BE
@Scheduled(cron = "0 0 4 * * *")
public void runIntegrityCheck() {
    IntegrityCheckResult result = integrityCheckService.runIntegrityCheck();
    log.info("무결성 체크 완료: 전체={}, 누락={}, 고아={}, 수정={}, 전체리빌드={}",
        result.totalChecked(), result.missingInIndex(),
        result.orphanedInIndex(), result.fixed(), result.fullRebuildTriggered());
}
```

---

### Phase 6: AdminTriggerService 재시도 전환

**대상 파일**: `src/main/java/com/example/udtbe/domain/admin/service/AdminTriggerService.java`

```java
// AS-IS: Spring Batch로 실패 건 재처리
public void retryFailedBatch() {
    jobLauncher.run(contentBatchJob, jobParameters(type="failed"));
}

// TO-BE: 스트리밍 방식으로 재처리
public void retryFailedBatch() {
    // 1. FAILED 상태 등록 Job 재처리
    List<AdminContentRegisterJob> failedRegJobs =
        registerJobRepository.findByStatus(BatchStatus.FAILED);
    failedRegJobs.forEach(job -> retryRegisterJob(job));

    // 2. FAILED 상태 수정 Job 재처리
    List<AdminContentUpdateJob> failedUpJobs =
        updateJobRepository.findByStatus(BatchStatus.FAILED);
    failedUpJobs.forEach(job -> retryUpdateJob(job));

    // 3. FAILED 상태 삭제 Job 재처리
    List<AdminContentDeleteJob> failedDelJobs =
        deleteJobRepository.findByStatus(BatchStatus.FAILED);
    failedDelJobs.forEach(job -> retryDeleteJob(job));
}
```

---

### Phase 7: Deprecation 처리

즉시 삭제하지 않고 `@Deprecated` 어노테이션 표시:

| 파일 | 대상 |
|------|------|
| `BatchConfig.java` | `contentBatchJob()`, 각 Step/Reader/Processor/Writer 빈 |
| `BatchRetryProcessor.java` | 클래스 전체 |
| `BatchSkipListener.java` | 클래스 전체 |
| `StepStatsListener.java` | 클래스 전체 |
| `JobCompletionListener.java` | 클래스 전체 |
| `BatchJobCompleteListener.java` | 클래스 전체 |

---

## 3. 에러 처리 전략

```
[시나리오 1] DB 실패
    -> 트랜잭션 롤백
    -> Job 상태: FAILED (롤백 대상이므로 별도 트랜잭션 필요시 고려)
    -> API: 에러 응답 반환
    -> Lucene: 이벤트 미발행 (AFTER_COMMIT이므로)

[시나리오 2] DB 성공 + Lucene 실패
    -> Job 상태: COMPLETED (DB가 source of truth)
    -> Lucene: WARN 로깅
    -> 새벽 4시 무결성 체크가 자동 보정

[시나리오 3] 유효성 검증 실패
    -> Job 상태: INVALID
    -> API: RestApiException re-throw
    -> Lucene: 이벤트 미발행
```

---

## 4. 수정 대상 파일 요약

| 파일 | 변경 유형 | 우선순위 |
|------|-----------|----------|
| `LuceneIndexService.java` | 증분 메서드 추가 | P1 |
| `ContentStreamingType.java` | **신규** enum | P1 |
| `ContentStreamingEvent.java` | **신규** 이벤트 | P1 |
| `ContentStreamingEventListener.java` | **신규** 리스너 | P1 |
| `AdminService.java` | 스트리밍 즉시처리 전환 | P1 |
| `TimeUtil.java` | scheduledAt -> now | P2 |
| `IntegrityCheckResult.java` | **신규** record | P2 |
| `IntegrityCheckService.java` | **신규** 서비스 | P2 |
| `AdminScheduler.java` | 무결성 체크로 교체 | P2 |
| `AdminTriggerService.java` | 스트리밍 재시도 전환 | P3 |
| `BatchConfig.java` | @Deprecated 표시 | P3 |
| `BatchJobCompleteListener.java` | @Deprecated 표시 | P3 |

---

## 5. 검증 체크리스트

- [ ] LuceneIndexService 증분 메서드 단위 테스트
- [ ] AdminService 스트리밍 등록 -> Job COMPLETED + Lucene 인덱스 확인
- [ ] AdminService 스트리밍 수정 -> Job COMPLETED + Lucene 문서 업데이트 확인
- [ ] AdminService 스트리밍 삭제 -> Job COMPLETED + Lucene 문서 삭제 확인
- [ ] 유효성 검증 실패 시 Job INVALID + API 에러 응답
- [ ] 무결성 체크: DB-Lucene 불일치 감지 및 보정
- [ ] 무결성 체크: 10% 초과 불일치 시 전체 리빌드
- [ ] 기존 `/api/admin/batch` API 정상 응답
- [ ] 기존 배치 테스트 통과
- [ ] 재시도(retry) 엔드포인트 정상 작동
