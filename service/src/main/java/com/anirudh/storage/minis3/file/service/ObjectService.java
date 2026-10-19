package com.anirudh.storage.minis3.file.service;

import com.anirudh.storage.minis3.bucket.auth.BucketAuthService;
import com.anirudh.storage.minis3.bucket.entity.BucketStore;
import com.anirudh.storage.minis3.exception.InvalidRequestException;
import com.anirudh.storage.minis3.exception.ResourceNotFoundException;
import com.anirudh.storage.minis3.file.dto.ObjectContent;
import com.anirudh.storage.minis3.file.dto.ObjectDetails;
import com.anirudh.storage.minis3.file.dto.ObjectListResponse;
import com.anirudh.storage.minis3.file.dto.PutObjectResult;
import com.anirudh.storage.minis3.file.dto.StoredContent;
import com.anirudh.storage.minis3.file.entity.FileMetadata;
import com.anirudh.storage.minis3.file.mapper.FileMapper;
import com.anirudh.storage.minis3.file.repository.FileMetadataRepository;
import com.anirudh.storage.minis3.util.CommonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Limit;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * v2 object API: objects addressed by bucket name + key. The key is stored as FileMetadata.fileName;
 * bytes stay on disk under a random filePublicId, so keys never touch the filesystem.
 *
 * @author Anirudh
 * @since 19/10/26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObjectService {

    // matches the default length of file_metadata.file_name
    private static final int MAX_KEY_LENGTH = 255;
    public static final int DEFAULT_LIST_LIMIT = 100;
    private static final int MAX_LIST_LIMIT = 1000;

    private final FileMetadataRepository fileMetadataRepository;
    private final StorageService storageService;
    private final BucketAuthService bucketAuthService;
    private final FileMapper fileMapper;

    /**
     * Creates or replaces the object. A replacement is written under a new filePublicId and the old bytes are
     * removed only after the metadata points at the new ones, so readers never see a half-written object.
     * Concurrent writes to the same key: last writer wins.
     */
    public PutObjectResult putObject(String bucketName, String key, String contentType, InputStream content) {
        validateKey(key);
        BucketStore bucketStore = bucketAuthService.resolveAccessibleBucket(bucketName);
        return putObject(bucketStore, key, contentType, content);
    }

    /**
     * For callers that already authorized access to the bucket (e.g. a verified pre-signed URL).
     */
    public PutObjectResult putObject(BucketStore bucketStore, String key, String contentType, InputStream content) {
        validateKey(key);
        UUID bucketId = bucketStore.getBucketPublicId();

        Optional<FileMetadata> existing = fileMetadataRepository.findByBucketStoreAndFileName(bucketStore, key);
        UUID previousFileId = existing.map(FileMetadata::getFilePublicId).orElse(null);
        UUID newFileId = CommonUtils.generateRandomUUID();

        StoredContent stored = storageService.storeStream(bucketId, newFileId, content);

        FileMetadata metadata = existing.orElseGet(() -> {
            FileMetadata created = new FileMetadata();
            created.setBucketStore(bucketStore);
            created.setFileName(key);
            return created;
        });
        metadata.setFilePublicId(newFileId);
        metadata.setSize(stored.size());
        metadata.setEtag(stored.md5());
        metadata.setContentType(contentType == null || contentType.isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType);

        FileMetadata saved;
        try {
            saved = fileMetadataRepository.save(metadata);
        } catch (RuntimeException e) {
            log.error("Exception occurred while saving object {} in bucket {}", key, bucketStore.getBucketName(), e);
            deleteQuietly(bucketId, newFileId);
            throw e;
        }

        if (previousFileId != null) {
            deleteQuietly(bucketId, previousFileId);
        }
        log.info("object {} {} in bucket {}", key, previousFileId == null ? "created" : "replaced", bucketStore.getBucketName());
        return new PutObjectResult(saved, previousFileId == null);
    }

    public FileMetadata headObject(String bucketName, String key) {
        validateKey(key);
        BucketStore bucketStore = bucketAuthService.resolveAccessibleBucket(bucketName);
        return findObject(bucketStore, key);
    }

    public ObjectContent getObject(String bucketName, String key) {
        validateKey(key);
        BucketStore bucketStore = bucketAuthService.resolveAccessibleBucket(bucketName);
        return getObject(bucketStore, key);
    }

    /**
     * For callers that already authorized access to the bucket (e.g. a verified pre-signed URL).
     */
    public ObjectContent getObject(BucketStore bucketStore, String key) {
        validateKey(key);
        FileMetadata metadata = findObject(bucketStore, key);
        Resource resource = storageService.read(bucketStore.getBucketPublicId(), metadata.getFilePublicId());
        return new ObjectContent(metadata, resource);
    }

    public void deleteObject(String bucketName, String key) {
        validateKey(key);
        BucketStore bucketStore = bucketAuthService.resolveAccessibleBucket(bucketName);
        FileMetadata metadata = findObject(bucketStore, key);

        // metadata first: once it's gone the object is gone for readers, even if the file delete fails
        fileMetadataRepository.delete(metadata);
        deleteQuietly(bucketStore.getBucketPublicId(), metadata.getFilePublicId());
        log.info("object {} deleted from bucket {}", key, bucketName);
    }

    public ObjectListResponse listObjects(String bucketName, String prefix, String cursor, int limit) {
        BucketStore bucketStore = bucketAuthService.resolveAccessibleBucket(bucketName);
        String safePrefix = prefix == null ? "" : prefix;
        String after = cursor == null || cursor.isBlank() ? "" : decodeCursor(cursor);
        int pageSize = Math.clamp(limit, 1, MAX_LIST_LIMIT);

        // fetch one extra row to know whether another page exists
        List<FileMetadata> rows = fileMetadataRepository
                .findByBucketStoreAndFileNameStartingWithAndFileNameGreaterThanOrderByFileNameAsc(
                        bucketStore, safePrefix, after, Limit.of(pageSize + 1));

        boolean truncated = rows.size() > pageSize;
        List<FileMetadata> page = truncated ? rows.subList(0, pageSize) : rows;
        String nextCursor = truncated ? encodeCursor(page.getLast().getFileName()) : null;

        List<ObjectDetails> objects = page.stream().map(fileMapper::toObjectDetails).toList();
        return new ObjectListResponse(bucketName, safePrefix, objects, nextCursor, truncated);
    }

    private FileMetadata findObject(BucketStore bucketStore, String key) {
        return fileMetadataRepository.findByBucketStoreAndFileName(bucketStore, key)
                .orElseThrow(() -> new ResourceNotFoundException("Object not found"));
    }

    public static void validateKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new InvalidRequestException("Object key must not be empty");
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new InvalidRequestException("Object key must be at most " + MAX_KEY_LENGTH + " characters");
        }
        if (key.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) {
            throw new InvalidRequestException("Object key must not contain control characters");
        }
    }

    private void deleteQuietly(UUID bucketId, UUID fileId) {
        try {
            storageService.delete(bucketId, fileId);
        } catch (Exception e) {
            log.error("Cleanup failed for file {} in bucket {}", fileId, bucketId, e);
        }
    }

    private String encodeCursor(String key) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeCursor(String cursor) {
        try {
            return new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Invalid cursor");
        }
    }
}
