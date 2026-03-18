package com.anirudh.storage.minis3.bucket.service;

import com.anirudh.storage.minis3.bucket.dto.CreateBucketRequest;
import com.anirudh.storage.minis3.bucket.entity.BucketStore;
import com.anirudh.storage.minis3.bucket.mapper.BucketMapper;
import com.anirudh.storage.minis3.bucket.repository.BucketStoreRepository;
import com.anirudh.storage.minis3.exception.BucketAlreadyExistsException;
import com.anirudh.storage.minis3.exception.ResourceNotFoundException;
import com.anirudh.storage.minis3.util.CommonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return bucketStoreRepository.findByOwnerId(ownerId, pageable);


    }

    @Transactional
    public BucketStore deleteBucket(UUID bucketId) {
        log.info("deleting the bucket {}", bucketId);

        BucketStore bucketStore = bucketStoreRepository
                .findByBucketPublicId(bucketId)
                .orElseThrow(() -> new ResourceNotFoundException("Bucket not Found"));
        bucketStoreRepository.delete(bucketStore);

        return bucketStore;
    }
}
