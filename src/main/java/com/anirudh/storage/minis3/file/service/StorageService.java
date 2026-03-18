package com.anirudh.storage.minis3.file.service;

import com.anirudh.storage.minis3.file.dto.UploadFileRequest;
import com.anirudh.storage.minis3.file.entity.FileMetadata;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.UUID;

/**
 * @author Anirudh
 * @since 04/03/26
 */
public interface StorageService {
    void store(UUID bucketId, UploadFileRequest uploadFileRequest, FileMetadata fileMetadata);

    Resource read(UUID bucketId, UUID fileId);

    void delete(UUID bucketId, UUID fileId) throws IOException;
}
