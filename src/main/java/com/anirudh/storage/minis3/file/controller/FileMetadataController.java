package com.anirudh.storage.minis3.file.controller;

import com.anirudh.storage.minis3.bucket.auth.BucketAuthService;
import com.anirudh.storage.minis3.file.dto.FileDetailsResponse;
import com.anirudh.storage.minis3.file.dto.FileDownloadResponse;
import com.anirudh.storage.minis3.file.dto.UploadFileRequest;
import com.anirudh.storage.minis3.file.entity.FileMetadata;
import com.anirudh.storage.minis3.file.mapper.FileMapper;
import com.anirudh.storage.minis3.file.service.FileMetadataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * @author Anirudh
 * @since 02/03/26
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/file")
public class FileMetadataController {
    private final FileMetadataService fileMetadataService;
    private final FileMapper fileMapper;
    private final BucketAuthService bucketAuthService;

    @GetMapping("/buckets/{bucketId}/files")
    @PreAuthorize("hasAuthority('SCOPE_READ') and @bucketAuthService.canAccess(#bucketPublicId)")
    public ResponseEntity<Page<FileDetailsResponse>> getFiles(@PathVariable("bucketId") UUID bucketPublicId,
                                                              @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<FileMetadata> fileMetadataPage = fileMetadataService.getFileDetails(bucketPublicId, pageable);
        Page<FileDetailsResponse> fileDetailsPage = fileMetadataPage.map(fileMetadata -> fileMapper.toDto(fileMetadata, FileDetailsResponse.class));

        return ResponseEntity.ok(fileDetailsPage);
    }

    @PostMapping(value = "/buckets/{bucketId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SCOPE_WRITE') and @bucketAuthService.canAccess(#bucketId)")
    public ResponseEntity<FileDetailsResponse> uploadFile(@PathVariable UUID bucketId, @Valid @ModelAttribute UploadFileRequest uploadFileRequest) {


        FileMetadata fileMetadata = fileMetadataService.uploadFile(uploadFileRequest, bucketId);
        FileDetailsResponse fileDetailsResponse = fileMapper.toDto(fileMetadata, FileDetailsResponse.class);
        URI uri = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .replacePath("/api/v1/file/buckets/{bucketId}/{filePublicId}/files")
                .buildAndExpand(bucketId, fileDetailsResponse.getFilePublicId())
                .toUri();
        return ResponseEntity.created(uri).body(fileDetailsResponse);
    }


    @GetMapping(value = "/buckets/{bucketPublicId}/files/{filePublicId}")
    @PreAuthorize("hasAuthority('SCOPE_READ') and @bucketAuthService.canAccess(#bucketId)")
    public ResponseEntity<Resource> downloadFile(@PathVariable("bucketPublicId") UUID bucketId, @PathVariable("filePublicId") UUID filePublicId) {

        FileDownloadResponse file = fileMetadataService.downloadFile(bucketId, filePublicId);

        return ResponseEntity
                .ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.getFileName() + "\"")
                .contentLength(file.getSize())
                .body(file.getResource());

    }


}
