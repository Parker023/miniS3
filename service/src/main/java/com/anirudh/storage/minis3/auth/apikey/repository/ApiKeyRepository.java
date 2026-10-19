package com.anirudh.storage.minis3.auth.apikey.repository;

import com.anirudh.storage.minis3.auth.apikey.entity.ApiKey;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * @author Anirudh
 * @since 19/10/26
 */
@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByPrefix(String prefix);

    Optional<ApiKey> findByKeyPublicId(UUID keyPublicId);

    Page<ApiKey> findByOwnerId(UUID ownerId, Pageable pageable);

    Optional<ApiKey> findByKeyPublicIdAndOwnerId(UUID keyPublicId, UUID ownerId);

    long deleteByOwnerId(UUID ownerId);
}
