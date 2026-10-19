package com.anirudh.storage.minis3.file.repository;

import com.anirudh.storage.minis3.bucket.entity.BucketStore;
import com.anirudh.storage.minis3.file.entity.FileMetadata;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Anirudh
 * @since 02/03/26
 */
public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {

    Page<FileMetadata> findByBucketStore_BucketPublicId(UUID bucketPublicId, Pageable pageable);

    Optional<FileMetadata> findByBucketStore_BucketPublicIdAndFilePublicId(UUID bucketPublicId, UUID filePublicId);

    boolean existsByBucketStore_BucketPublicIdAndFileNameIgnoreCase(UUID bucketPublicId, String fileName);

    Optional<FileMetadata> findByBucketStoreAndFileName(BucketStore bucketStore, String fileName);

    @Modifying
    @Query("delete from FileMetadata f where f.bucketStore = :bucketStore")
    int deleteAllInBucket(@Param("bucketStore") BucketStore bucketStore);

    // keyset pagination for the v2 object listing: keys under prefix, after the cursor, in key order
    List<FileMetadata> findByBucketStoreAndFileNameStartingWithAndFileNameGreaterThanOrderByFileNameAsc(
            BucketStore bucketStore, String prefix, String after, Limit limit);

}
