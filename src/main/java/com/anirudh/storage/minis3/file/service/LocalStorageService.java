package com.anirudh.storage.minis3.file.service;

import com.anirudh.storage.minis3.exception.EmptyFileException;
import com.anirudh.storage.minis3.exception.FileAccessDeniedException;
import com.anirudh.storage.minis3.exception.FileAlreadyExistsException;
import com.anirudh.storage.minis3.exception.ResourceNotFoundException;
import com.anirudh.storage.minis3.exception.StorageException;
import com.anirudh.storage.minis3.exception.dto.FileSizeLimitExceededException;
import com.anirudh.storage.minis3.file.dto.UploadFileRequest;
import com.anirudh.storage.minis3.file.entity.FileMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * @author Anirudh
 * @since 04/03/26
 */
@Slf4j
@Service("LocalStorageService")
public class LocalStorageService implements StorageService {

    @Value("${minis3.upload.max-size}")
    private String uploadMaxSize;
    @Value("${minis3.app.storage.location}")
    private String storageRoot;

    @Override
    public void store(UUID bucketId, UploadFileRequest uploadFileRequest, FileMetadata fileMetadata) {

        MultipartFile file = uploadFileRequest.getMultipartFile();
        log.info("uploading file {} to local storage", file.getOriginalFilename());

        // checks if the file is empty
        if (uploadFileRequest.getMultipartFile() == null || uploadFileRequest.getMultipartFile().isEmpty()) {
            throw new EmptyFileException("File is empty");
        }

        long fileSizeMax = DataSize.parse(uploadMaxSize).toBytes();

        if (uploadFileRequest.getMultipartFile().getSize() > fileSizeMax) {
            throw new FileSizeLimitExceededException("File size limit exceeded");
        }


        Path bucketPath = Paths.get(storageRoot, bucketId.toString());
        try {
            if (!Files.exists(bucketPath)) {

                Files.createDirectories(bucketPath);
                log.info("bucket {} created", bucketId);

            }
            Path fileStorageLocation = bucketPath.resolve(fileMetadata.getFilePublicId().toString());

            if (Files.exists(fileStorageLocation)) {
                throw new FileAlreadyExistsException("File already exists");
            }

            uploadFileRequest.getMultipartFile().transferTo(fileStorageLocation);

        } catch (IOException e) {
            log.error("Error storing file for bucket {}", bucketId, e);
            throw new StorageException(e.getMessage());
        }
    }

    @Override
    public Resource read(UUID bucketId, UUID fileId) {

        doFilePathExistsChecks(bucketId, fileId);

        Path filePath = Paths.get(storageRoot)
                .resolve(bucketId.toString())
                .resolve(fileId.toString());

        try {
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()) {
                throw new ResourceNotFoundException("File not found " + fileId);
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to read file ", e);
        }


    }

    @Override
    public void delete(UUID bucketId, UUID fileId) throws IOException {

        doFilePathExistsChecks(bucketId, fileId);

        Path filePath = Paths.get(storageRoot, bucketId.toString()).resolve(fileId.toString());

        Files.delete(filePath);
        log.debug("file deleted {} successfully", filePath);

    }

    private void doFilePathExistsChecks(UUID bucketId, UUID fileId) {
        if (fileId == null) {
            log.error("file Id is null");
            //TODO create other exception for file Id null
            throw new FileAccessDeniedException("file Id is null");
        }

        Path bucketPath = Paths.get(storageRoot, bucketId.toString());
        if (!Files.exists(bucketPath)) {
            log.error("path with bucketId doesn't exist");
            String exceptionMessage = "Bucket id :" + bucketId + " doesn't exist";
            throw new ResourceNotFoundException(exceptionMessage);
        }
        Path filePath = bucketPath.resolve(fileId.toString());
        if (!Files.exists(filePath)) {
            log.error("file doesn't exist");
            String exceptionMessage = "file :" + filePath + " doesn't exist";
            throw new ResourceNotFoundException(exceptionMessage);

        }
    }
}
