package com.example.udtbe.domain.content.repository;

import com.example.udtbe.domain.content.entity.Content;
import com.example.udtbe.domain.content.entity.ContentCategory;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ContentCategoryRepository extends JpaRepository<ContentCategory, Long> {

    void deleteAllByContent(Content content);

    @Modifying
    @Query("delete from ContentCategory cc where cc.content.isDeleted = true and cc.content.updatedAt < :threshold")
    int hardDeleteByContentSoftDeletedBefore(LocalDateTime threshold);
}
