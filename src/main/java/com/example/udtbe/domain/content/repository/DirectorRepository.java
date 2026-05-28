package com.example.udtbe.domain.content.repository;

import com.example.udtbe.domain.content.entity.Director;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DirectorRepository extends JpaRepository<Director, Long>, DirectorQueryDSL {

    Optional<Director> findByDirectorName(String directorName);

    @Modifying
    @Query("delete from Director d where d.isDeleted = true and d.updatedAt < :threshold")
    int hardDeleteSoftDeletedBefore(LocalDateTime threshold);
}
