package com.example.udtbe.domain.content.repository;

import com.example.udtbe.domain.content.entity.Cast;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CastRepository extends JpaRepository<Cast, Long>, CastQueryDSL {

    Optional<Cast> findByCastNameAndCastImageUrl(String castName, String castImageUrl);

    @Modifying
    @Query("delete from Cast c where c.isDeleted = true and c.updatedAt < :threshold")
    int hardDeleteSoftDeletedBefore(LocalDateTime threshold);
}
