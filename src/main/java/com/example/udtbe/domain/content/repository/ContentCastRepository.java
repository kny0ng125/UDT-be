package com.example.udtbe.domain.content.repository;

import com.example.udtbe.domain.content.entity.Content;
import com.example.udtbe.domain.content.entity.ContentCast;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ContentCastRepository extends JpaRepository<ContentCast, Long> {

    void deleteAllByContent(Content content);

    boolean existsByCast_IdAndContent_IsDeletedFalse(Long castId);

    @Modifying
    @Query("delete from ContentCast cc where cc.content.isDeleted = true and cc.content.updatedAt < :threshold")
    int hardDeleteByContentSoftDeletedBefore(LocalDateTime threshold);

    @Modifying
    @Query("delete from ContentCast cc where cc.cast.isDeleted = true and cc.cast.updatedAt < :threshold")
    int hardDeleteByCastSoftDeletedBefore(LocalDateTime threshold);
}
