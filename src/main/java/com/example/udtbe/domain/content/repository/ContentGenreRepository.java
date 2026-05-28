package com.example.udtbe.domain.content.repository;

import com.example.udtbe.domain.content.entity.Content;
import com.example.udtbe.domain.content.entity.ContentGenre;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ContentGenreRepository extends JpaRepository<ContentGenre, Long> {

    void deleteAllByContent(Content content);

    @Modifying
    @Query("delete from ContentGenre cg where cg.content.isDeleted = true and cg.content.updatedAt < :threshold")
    int hardDeleteByContentSoftDeletedBefore(LocalDateTime threshold);
}
