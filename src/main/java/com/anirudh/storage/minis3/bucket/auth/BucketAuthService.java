package com.anirudh.storage.minis3.bucket.auth;

import com.anirudh.storage.minis3.auth.dto.AuthenticatedUser;
import com.anirudh.storage.minis3.bucket.entity.BucketStore;
import com.anirudh.storage.minis3.bucket.repository.BucketStoreRepository;
import com.anirudh.storage.minis3.exception.BucketAccessDeniedException;
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

    /**
     * True when the caller owns the bucket and, for API keys, the key isn't restricted away from it.
     * Scope (READ/WRITE/DELETE) is checked separately with hasAuthority in each @PreAuthorize.
     */
    public boolean canAccess(UUID bucketPublicId) {
        BucketStore bucketStore = bucketStoreRepository
                .findByBucketPublicId(bucketPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Bucket not Found"));

        AuthenticatedUser user = CommonUtils.getAuthenticatedUser();
        return bucketStore.getOwnerId().equals(user.getUserPublicId()) && user.canAccessBucket(bucketPublicId);
    }

    /**
     * Resolves a bucket by name within the caller's own buckets (names are unique per owner),
     * rejecting buckets an API key is restricted away from.
     */
    public BucketStore resolveAccessibleBucket(String bucketName) {
        AuthenticatedUser user = CommonUtils.getAuthenticatedUser();
        BucketStore bucketStore = bucketStoreRepository
                .findByOwnerIdAndBucketName(user.getUserPublicId(), bucketName)
                .orElseThrow(() -> new ResourceNotFoundException("Bucket not Found"));

        if (!user.canAccessBucket(bucketStore.getBucketPublicId())) {
            throw new BucketAccessDeniedException("Bucket Access Denied");
        }
        return bucketStore;
    }
}
