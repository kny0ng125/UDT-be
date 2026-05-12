package com.example.udtbe.content.event;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.example.udtbe.common.fixture.ContentFixture;
import com.example.udtbe.common.fixture.ContentMetadataFixture;
import com.example.udtbe.domain.content.entity.Content;
import com.example.udtbe.domain.content.entity.ContentMetadata;
import com.example.udtbe.domain.content.event.ContentStreamingEvent;
import com.example.udtbe.domain.content.event.ContentStreamingEventListener;
import com.example.udtbe.domain.content.event.ContentStreamingType;
import com.example.udtbe.domain.content.service.LuceneIndexService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentStreamingEventListenerTest {

    @Mock
    private LuceneIndexService luceneIndexService;

    @InjectMocks
    private ContentStreamingEventListener listener;

    @DisplayName("REGISTER 이벤트 수신 시 addDocument를 호출한다")
    @Test
    void handleRegister_callsAddDocument() {
        // given
        Content content = ContentFixture.parasite();
        ContentMetadata metadata = ContentMetadataFixture.metadata(content, "NETFLIX", "ACTION");
        ContentStreamingEvent event = ContentStreamingEvent.of(
                this, ContentStreamingType.REGISTER, 1L, metadata);

        // when
        listener.handleContentStreaming(event);

        // then
        verify(luceneIndexService).addDocument(metadata);
        verifyNoMoreInteractions(luceneIndexService);
    }

    @DisplayName("UPDATE 이벤트 수신 시 updateDocument를 호출한다")
    @Test
    void handleUpdate_callsUpdateDocument() {
        // given
        Content content = ContentFixture.parasite();
        ContentMetadata metadata = ContentMetadataFixture.metadata(content, "WATCHA", "COMEDY");
        ContentStreamingEvent event = ContentStreamingEvent.of(
                this, ContentStreamingType.UPDATE, 1L, metadata);

        // when
        listener.handleContentStreaming(event);

        // then
        verify(luceneIndexService).updateDocument(1L, metadata);
        verifyNoMoreInteractions(luceneIndexService);
    }

    @DisplayName("DELETE 이벤트 수신 시 deleteDocument를 호출한다")
    @Test
    void handleDelete_callsDeleteDocument() {
        // given
        ContentStreamingEvent event = ContentStreamingEvent.of(
                this, ContentStreamingType.DELETE, 99L, null);

        // when
        listener.handleContentStreaming(event);

        // then
        verify(luceneIndexService).deleteDocument(99L);
        verifyNoMoreInteractions(luceneIndexService);
    }
}
