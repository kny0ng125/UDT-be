package com.example.udtbe.domain.content.repository;

import com.example.udtbe.domain.content.entity.Content;
import com.example.udtbe.domain.content.entity.ContentPlatform;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ContentPlatformRepository extends JpaRepository<ContentPlatform, Long> {

    void deleteAllByContent(Content content);

    @Modifying
    @Query("delete from ContentPlatform cp where cp.content.isDeleted = true and cp.content.updatedAt < :threshold")
    int hardDeleteByContentSoftDeletedBefore(LocalDateTime threshold);
}
