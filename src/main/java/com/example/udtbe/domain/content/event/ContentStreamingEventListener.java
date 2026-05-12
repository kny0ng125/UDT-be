package com.example.udtbe.domain.content.event;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

import com.example.udtbe.domain.content.service.LuceneIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContentStreamingEventListener {

    private final LuceneIndexService luceneIndexService;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void handleContentStreaming(ContentStreamingEvent event) {
        log.info("컨텐츠 스트리밍 이벤트 수신 - type={}, contentId={}",
                event.getType(), event.getContentId());

        switch (event.getType()) {
            case REGISTER -> luceneIndexService.addDocument(event.getMetadata());
            case UPDATE -> luceneIndexService.updateDocument(
                    event.getContentId(), event.getMetadata());
            case DELETE -> luceneIndexService.deleteDocument(event.getContentId());
        }
    }
}
