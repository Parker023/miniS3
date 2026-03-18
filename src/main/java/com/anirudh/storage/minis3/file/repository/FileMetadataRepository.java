package com.anirudh.storage.minis3.file.repository;

import com.anirudh.storage.minis3.file.entity.FileMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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

}
