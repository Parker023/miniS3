package com.anirudh.storage.minis3.file.service;

import com.anirudh.storage.minis3.exception.EmptyFileException;
import com.anirudh.storage.minis3.exception.FileAccessDeniedException;
import com.anirudh.storage.minis3.exception.FileAlreadyExistsException;
import com.anirudh.storage.minis3.exception.ResourceNotFoundException;
import com.anirudh.storage.minis3.exception.StorageException;
import com.anirudh.storage.minis3.exception.dto.FileSizeLimitExceededException;
import com.anirudh.storage.minis3.file.dto.StoredContent;
import com.anirudh.storage.minis3.file.dto.UploadFileRequest;
import com.anirudh.storage.minis3.file.entity.FileMetadata;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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

    // fail at startup, not on the first upload, if the storage directory is missing or read-only
    @PostConstruct
    void checkStorageRoot() throws IOException {
        Path root = Paths.get(storageRoot).toAbsolutePath();
        Files.createDirectories(root);
        if (!Files.isWritable(root)) {
            throw new IllegalStateException("Storage location " + root + " is not writable");
        }
        log.info("storing objects under {}", root);
    }

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

    @Override
    public StoredContent storeStream(UUID bucketId, UUID fileId, InputStream content) {
        long maxBytes = DataSize.parse(uploadMaxSize).toBytes();
        Path bucketPath = Paths.get(storageRoot, bucketId.toString());
        Path tempFile = null;

        try {
            Files.createDirectories(bucketPath);
            // write next to the final location so the move below is atomic
            tempFile = Files.createTempFile(bucketPath, ".upload-", ".tmp");
            MessageDigest md5 = MessageDigest.getInstance("MD5");

            long size;
            try (InputStream in = new DigestInputStream(content, md5);
                 OutputStream out = Files.newOutputStream(tempFile)) {
                size = copyWithLimit(in, out, maxBytes);
            }

            Files.move(tempFile, bucketPath.resolve(fileId.toString()), StandardCopyOption.ATOMIC_MOVE);
            log.info("stored {} bytes as {} in bucket {}", size, fileId, bucketId);
            return new StoredContent(size, HexFormat.of().formatHex(md5.digest()));

        } catch (IOException e) {
            log.error("Error storing file for bucket {}", bucketId, e);
            throw new StorageException(e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        } finally {
            deleteTempFile(tempFile);
        }
    }

    @Override
    public void deleteBucket(UUID bucketId) throws IOException {
        Path bucketPath = Paths.get(storageRoot, bucketId.toString());
        if (Files.exists(bucketPath)) {
            FileUtils.deleteDirectory(bucketPath.toFile());
            log.info("deleted storage directory for bucket {}", bucketId);
        }
    }

    private long copyWithLimit(InputStream in, OutputStream out, long maxBytes) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new FileSizeLimitExceededException("File size limit exceeded");
            }
            out.write(buffer, 0, read);
        }
        return total;
    }

    private void deleteTempFile(Path tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            // no-op once the temp file has been moved into place
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            log.warn("could not delete temp file {}", tempFile, e);
        }
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
