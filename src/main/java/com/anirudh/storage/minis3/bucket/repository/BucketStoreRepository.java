package com.anirudh.storage.minis3.bucket.repository;

import com.anirudh.storage.minis3.bucket.entity.BucketStore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Anirudh
 * @since 24/02/26
 */

@Repository
public interface BucketStoreRepository extends JpaRepository<BucketStore, Long> {

    @Query(value = "select count(id) from bucket_store where bucket_name=:bucketName and owner_id=:ownerId", nativeQuery = true)
    int findBucketCountByOwnerId(@Param("bucketName") String bucketName,
                                 @Param("ownerId") UUID ownerId);

    Page<BucketStore> findByOwnerId(@Param("ownerId") UUID ownerId, Pageable pageable);

    Optional<BucketStore> findByBucketPublicId(UUID bucketPublicId);

    Optional<BucketStore> findByOwnerIdAndBucketName(UUID ownerId, String bucketName);

    @Modifying
    void deleteByBucketPublicId(UUID bucketId);

    boolean existsByBucketPublicIdAndOwnerId(UUID bucketPublicId, UUID ownerId);

    boolean existsByBucketPublicId(UUID bucketPublicId);

    Page<BucketStore> findByOwnerIdAndBucketPublicIdIn(UUID ownerId, Collection<UUID> bucketPublicIds, Pageable pageable);

    long countByOwnerIdAndBucketPublicIdIn(UUID ownerId, Collection<UUID> bucketPublicIds);
}


