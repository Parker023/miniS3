package com.anirudh.storage.minis3.bucket.service;

import com.anirudh.storage.minis3.bucket.dto.CreateBucketRequest;
import com.anirudh.storage.minis3.bucket.entity.BucketStore;
import com.anirudh.storage.minis3.bucket.mapper.BucketMapper;
import com.anirudh.storage.minis3.bucket.repository.BucketStoreRepository;
import com.anirudh.storage.minis3.exception.BucketAlreadyExistsException;
import com.anirudh.storage.minis3.exception.ResourceNotFoundException;
import com.anirudh.storage.minis3.file.repository.FileMetadataRepository;
import com.anirudh.storage.minis3.file.service.StorageService;
import com.anirudh.storage.minis3.util.CommonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Anirudh
 * @since 26/02/26
 */
@ExtendWith(MockitoExtension.class)
class BucketStoreServiceTest {

    @Mock
    private BucketMapper bucketMapper;

    @Mock
    private BucketStoreRepository bucketStoreRepository;

    @Mock
    private FileMetadataRepository fileMetadataRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private BucketStoreService bucketStoreService;
    UUID ownerId;
    UUID bucketId;
    private CreateBucketRequest createBucketRequest;

    private BucketStore bucketStore;
    Page<BucketStore> page;

    @BeforeEach
    void setup() {
        SecurityContextHolder.clearContext();
        ownerId = CommonUtils.generateRandomUUID();
        bucketId = CommonUtils.generateRandomUUID();
        createBucketRequest = new CreateBucketRequest();
        createBucketRequest.setBucketName("bucket");
        bucketStore = new BucketStore();

        bucketStore.setBucketPublicId(bucketId);
        bucketStore.setBucketName("bucket");
        bucketStore.setOwnerId(ownerId);
        page = Page.empty(Pageable.ofSize(20));
    }

    @Test
    void createBucket() {

        try (MockedStatic<CommonUtils> mockedStatic = Mockito.mockStatic(CommonUtils.class)) {
            mockedStatic.when(CommonUtils::getUserIdFromSecurityContext).thenReturn(ownerId);
            when(bucketStoreRepository.findBucketCountByOwnerId("bucket", ownerId)).thenReturn(0);
            when(bucketMapper.toEntity(createBucketRequest, ownerId))
                    .thenReturn(bucketStore);
            when(bucketStoreRepository.save(bucketStore))
                    .thenReturn(bucketStore);

            BucketStore bucket = bucketStoreService.createBucket(createBucketRequest);
            verify(bucketStoreRepository).findBucketCountByOwnerId(anyString(), eq(ownerId));
            verify(bucketStoreRepository).save(bucketStore);
            verify(bucketMapper).toEntity(createBucketRequest, ownerId);

            assertNotNull(bucket.getBucketPublicId());
            assertEquals("bucket", bucket.getBucketName());
            assertEquals(ownerId, bucket.getOwnerId());
        }

    }

    @Test
    void createBucket_Exception() {

        try (MockedStatic<CommonUtils> mockedStatic = Mockito.mockStatic(CommonUtils.class)) {
            mockedStatic.when(CommonUtils::getUserIdFromSecurityContext).thenReturn(ownerId);
            when(bucketStoreRepository.findBucketCountByOwnerId("bucket", ownerId)).thenReturn(1);

            assertThrows(BucketAlreadyExistsException.class, () -> bucketStoreService.createBucket(createBucketRequest));

            verify(bucketStoreRepository).findBucketCountByOwnerId(anyString(), eq(ownerId));
            verify(bucketStoreRepository, never()).save(bucketStore);
            verify(bucketMapper, never()).toEntity(any(), any());


        }

    }

    @Test
    void getBucketStoreDetails() {
        try (MockedStatic<CommonUtils> mockedStatic = Mockito.mockStatic(CommonUtils.class)) {
            mockedStatic.when(CommonUtils::getUserIdFromSecurityContext).thenReturn(ownerId);

            when(bucketStoreRepository.findByOwnerId(ownerId, Pageable.ofSize(20))).thenReturn(page);

            Page<BucketStore> bucketStorePage = bucketStoreService.getBucketStoreDetails(Pageable.ofSize(20));

            verify(bucketStoreRepository).findByOwnerId(any(), any());

            assertEquals(page.getSize(), bucketStorePage.getSize());
            assertNotNull(bucketStorePage);
        }

    }

    @Test
    void deleteBucket() throws IOException {
        when(bucketStoreRepository.findByBucketPublicId(bucketId)).thenReturn(Optional.ofNullable(bucketStore));

        BucketStore deletedBucket = bucketStoreService.deleteBucket(bucketId);
        verify(bucketStoreRepository).findByBucketPublicId(bucketId);
        assertEquals(bucketId, deletedBucket.getBucketPublicId());
        // file rows go before the bucket row, and (outside a transaction) the bytes right away
        verify(fileMetadataRepository).deleteAllInBucket(bucketStore);
        verify(bucketStoreRepository).delete(bucketStore);
        verify(storageService).deleteBucket(bucketId);
    }

    @Test
    void deleteBucket_Exception() {

        when(bucketStoreRepository.findByBucketPublicId(bucketId)).thenThrow(ResourceNotFoundException.class);
        assertThrows(ResourceNotFoundException.class, () -> bucketStoreService.deleteBucket(bucketId));

        verify(bucketStoreRepository).findByBucketPublicId(bucketId);
    }
}