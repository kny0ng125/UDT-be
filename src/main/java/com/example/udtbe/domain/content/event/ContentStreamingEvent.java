package com.example.udtbe.domain.content.event;

import com.example.udtbe.domain.content.entity.ContentMetadata;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ContentStreamingEvent extends ApplicationEvent {

    private final ContentStreamingType type;
    private final Long contentId;
    private final ContentMetadata metadata;

    private ContentStreamingEvent(Object source, ContentStreamingType type,
            Long contentId, ContentMetadata metadata) {
        super(source);
        this.type = type;
        this.contentId = contentId;
        this.metadata = metadata;
    }

    public static ContentStreamingEvent of(Object source, ContentStreamingType type,
            Long contentId, ContentMetadata metadata) {
        return new ContentStreamingEvent(source, type, contentId, metadata);
    }
}
