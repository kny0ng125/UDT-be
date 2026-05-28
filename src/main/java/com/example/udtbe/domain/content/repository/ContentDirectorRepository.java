package com.example.udtbe.domain.content.repository;

import com.example.udtbe.domain.content.entity.Content;
import com.example.udtbe.domain.content.entity.ContentDirector;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ContentDirectorRepository extends JpaRepository<ContentDirector, Long> {

    void deleteAllByContent(Content content);

    boolean existsByDirector_IdAndContent_IsDeletedFalse(Long directorId);

    @Modifying
    @Query("delete from ContentDirector cd where cd.content.isDeleted = true and cd.content.updatedAt < :threshold")
    int hardDeleteByContentSoftDeletedBefore(LocalDateTime threshold);

    @Modifying
    @Query("delete from ContentDirector cd where cd.director.isDeleted = true and cd.director.updatedAt < :threshold")
    int hardDeleteByDirectorSoftDeletedBefore(LocalDateTime threshold);
}
