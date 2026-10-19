package com.anirudh.storage.minis3.bucket.service;

import com.anirudh.storage.minis3.bucket.dto.CreateBucketRequest;
import com.anirudh.storage.minis3.bucket.entity.BucketStore;
import com.anirudh.storage.minis3.bucket.mapper.BucketMapper;
import com.anirudh.storage.minis3.bucket.repository.BucketStoreRepository;
import com.anirudh.storage.minis3.exception.BucketAlreadyExistsException;
import com.anirudh.storage.minis3.file.repository.FileMetadataRepository;
import com.anirudh.storage.minis3.file.service.StorageService;
import com.anirudh.storage.minis3.exception.ResourceNotFoundException;
import com.anirudh.storage.minis3.util.CommonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * @author Anirudh
 * @since 24/02/26
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BucketStoreService {

    private final BucketMapper bucketMapper;

    private final BucketStoreRepository bucketStoreRepository;

    private final FileMetadataRepository fileMetadataRepository;

    private final StorageService storageService;

    @Transactional
    public BucketStore createBucket(CreateBucketRequest createBucketRequest) {

        UUID ownerId = CommonUtils.getUserIdFromSecurityContext();
        int bucketCount = bucketStoreRepository.findBucketCountByOwnerId(createBucketRequest.getBucketName(), ownerId);
        if (bucketCount > 0) {
            log.error("Bucket with name {} already exists", createBucketRequest.getBucketName());
            throw new BucketAlreadyExistsException();
        }

        BucketStore bucketStore = bucketMapper.toEntity(createBucketRequest, ownerId);

        bucketStoreRepository.save(bucketStore);
        log.info("bucket {} is created", bucketStore.getBucketName());

        return bucketStore;
    }

    public Page<BucketStore> getBucketStoreDetails(Pageable pageable) {
        UUID ownerId = CommonUtils.getUserIdFromSecurityContext();
        Set<UUID> allowedBucketIds = CommonUtils.getAllowedBucketIds();
        if (!allowedBucketIds.isEmpty()) {
            return bucketStoreRepository.findByOwnerIdAndBucketPublicIdIn(ownerId, allowedBucketIds, pageable);
        }
        return bucketStoreRepository.findByOwnerId(ownerId, pageable);


    }

    @Transactional
    public BucketStore deleteBucket(UUID bucketId) {
        log.info("deleting the bucket {}", bucketId);

        BucketStore bucketStore = bucketStoreRepository
                .findByBucketPublicId(bucketId)
                .orElseThrow(() -> new ResourceNotFoundException("Bucket not Found"));
        deleteWithContents(bucketStore);

        return bucketStore;
    }

    /**
     * Deletes the bucket's file metadata and the bucket in the current transaction, and its bytes on disk
     * only once that commits, so a rollback never leaves metadata pointing at deleted files.
     */
    @Transactional
    public void deleteWithContents(BucketStore bucketStore) {
        int files = fileMetadataRepository.deleteAllInBucket(bucketStore);
        bucketStoreRepository.delete(bucketStore);

        UUID bucketId = bucketStore.getBucketPublicId();
        runAfterCommit(() -> {
            try {
                storageService.deleteBucket(bucketId);
            } catch (IOException e) {
                log.error("bucket {} deleted but its storage directory could not be removed", bucketId, e);
            }
        });
        log.info("bucket {} deleted with {} files", bucketStore.getBucketName(), files);
    }

    private void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
