package com.anirudh.storage.minis3.bucket.auth;

import com.anirudh.storage.minis3.bucket.entity.BucketStore;
import com.anirudh.storage.minis3.bucket.repository.BucketStoreRepository;
import com.anirudh.storage.minis3.exception.ResourceNotFoundException;
import com.anirudh.storage.minis3.util.CommonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * @author Anirudh
 * @since 25/02/26
 */
@Service
@RequiredArgsConstructor
public class BucketAuthService {

    private final BucketStoreRepository bucketStoreRepository;

    public boolean isOwner(UUID bucketPublicId) {
        BucketStore bucketStore = bucketStoreRepository
                .findByBucketPublicId(bucketPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Bucket not Found"));

        UUID userIdFromSecurityContext = CommonUtils.getUserIdFromSecurityContext();
        return bucketStore.getOwnerId().equals(userIdFromSecurityContext);
    }
}
