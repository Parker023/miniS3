package com.anirudh.storage.minis3.file.service;

import com.anirudh.storage.minis3.bucket.entity.BucketStore;
import com.anirudh.storage.minis3.bucket.repository.BucketStoreRepository;
import com.anirudh.storage.minis3.exception.BucketAccessDeniedException;
import com.anirudh.storage.minis3.exception.FileAccessDeniedException;
import com.anirudh.storage.minis3.exception.FileAlreadyExistsException;
import com.anirudh.storage.minis3.exception.ResourceNotFoundException;
import com.anirudh.storage.minis3.file.dto.FileDownloadResponse;
import com.anirudh.storage.minis3.file.dto.UploadFileRequest;
import com.anirudh.storage.minis3.file.entity.FileMetadata;
import com.anirudh.storage.minis3.file.mapper.FileMapper;
import com.anirudh.storage.minis3.file.repository.FileMetadataRepository;
import com.anirudh.storage.minis3.util.CommonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * @author Anirudh
 * @since 02/03/26
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileMetadataService {

    private final FileMetadataRepository fileMetadataRepository;
    private final BucketStoreRepository bucketStoreRepository;
    private final FileMapper fileMapper;
    private final StorageService storageService;

    public Page<FileMetadata> getFileDetails(UUID bucketId, Pageable pageable) {
        validateBucketAccess(bucketId);
        return fileMetadataRepository.findByBucketStore_BucketPublicId(bucketId, pageable);

    }

    @Transactional
    public FileMetadata uploadFile(UploadFileRequest uploadFileRequest, UUID bucketPublicId) {
        validateBucketAccess(bucketPublicId);
        MultipartFile multipartFile = uploadFileRequest.getMultipartFile();
        boolean fileAlreadyExists = fileMetadataRepository.existsByBucketStore_BucketPublicIdAndFileNameIgnoreCase(bucketPublicId, multipartFile.getOriginalFilename());

        if (fileAlreadyExists) {
            log.error("file {} already exists for bucketId {}", multipartFile.getOriginalFilename(), bucketPublicId);
            throw new FileAlreadyExistsException("File Already Exists");
        }

        BucketStore bucketStore = bucketStoreRepository
                .findByBucketPublicId(bucketPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Bucket not Found"));

        FileMetadata fileMetadata = fileMapper.toEntity(uploadFileRequest);
        storageService.store(bucketPublicId, uploadFileRequest, fileMetadata);
        fileMetadata.setBucketStore(bucketStore);
        try {
            return fileMetadataRepository.save(fileMetadata);
        } catch (Exception e) {
            log.error("Exception occurred while uploading file", e);
            try {
                storageService.delete(bucketPublicId, fileMetadata.getFilePublicId());
            } catch (Exception cleanupEx) {
                log.error("Cleanup failed for file {}", fileMetadata.getFilePublicId(), cleanupEx);
            }
            throw e;
        }

    }

    @Transactional
    public FileDownloadResponse downloadFile(UUID bucketPublicId, UUID filePublicId) {
        if (bucketPublicId == null) {
            throw new BucketAccessDeniedException("Bucket Id is null");
        }
        if (filePublicId == null) {
            throw new FileAccessDeniedException("File Id is null");
        }

        FileMetadata metadata = fileMetadataRepository.findByBucketStore_BucketPublicIdAndFilePublicId(bucketPublicId, filePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        Resource resource = storageService.read(bucketPublicId, filePublicId);
        return new FileDownloadResponse(
                resource,
                metadata.getContentType(),
                metadata.getFileName(),
                metadata.getSize()
        );

    }


    public void validateBucketAccess(UUID bucketId) {
        if (bucketId == null) {
            log.error("bucket id is null");
            throw new ResourceNotFoundException("Bucket Id is null");
        }

        boolean bucketExistsByBucketId = bucketStoreRepository
                .existsByBucketPublicId(bucketId);
        if (!bucketExistsByBucketId) {
            throw new ResourceNotFoundException("Bucket not Found");
        }

        UUID ownerId = CommonUtils.getUserIdFromSecurityContext();

        boolean existsByBucketPublicIdAndOwnerId = bucketStoreRepository.existsByBucketPublicIdAndOwnerId(bucketId, ownerId);
        if (!existsByBucketPublicIdAndOwnerId) {
            throw new BucketAccessDeniedException("Bucket Access Denied");
        }

    }
}
