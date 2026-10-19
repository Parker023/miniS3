package com.anirudh.storage.minis3.file.controller;

import com.anirudh.storage.minis3.bucket.entity.BucketStore;
import com.anirudh.storage.minis3.constants.PresignMethod;
import com.anirudh.storage.minis3.file.dto.ObjectContent;
import com.anirudh.storage.minis3.file.dto.ObjectDetails;
import com.anirudh.storage.minis3.file.dto.PresignRequest;
import com.anirudh.storage.minis3.file.dto.PresignedUrlResponse;
import com.anirudh.storage.minis3.file.dto.PutObjectResult;
import com.anirudh.storage.minis3.file.mapper.FileMapper;
import com.anirudh.storage.minis3.file.service.ObjectService;
import com.anirudh.storage.minis3.file.service.PresignService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.UUID;

import static com.anirudh.storage.minis3.file.controller.ObjectResponses.rejectFormContent;
import static com.anirudh.storage.minis3.file.controller.ObjectResponses.stripLeadingSlash;
import static com.anirudh.storage.minis3.file.controller.ObjectResponses.withObjectHeaders;

/**
 * Issuing pre-signed URLs (authenticated) and serving them (public, the signature is the credential).
 *
 * @author Anirudh
 * @since 19/10/26
 */
@RestController
@Tag(name = "Pre-signed URLs", description = "Issue and use time-limited links")
@RequiredArgsConstructor
public class PresignController {

    private final PresignService presignService;
    private final ObjectService objectService;
    private final FileMapper fileMapper;

    // scope (READ for GET, WRITE for PUT) and bucket access are checked in PresignService
    @PostMapping("/api/v2/buckets/{bucketName}/presign")
    public ResponseEntity<PresignedUrlResponse> presign(@PathVariable("bucketName") String bucketName,
                                                        @RequestBody @Valid PresignRequest request) {
        return ResponseEntity.ok(presignService.presign(bucketName, request.getKey(), request.getMethod(), request.getExpiresInSeconds()));
    }

    @SecurityRequirements
    @GetMapping("/api/v2/presigned/{bucketId}/{*key}")
    public ResponseEntity<Resource> presignedGet(@PathVariable("bucketId") UUID bucketId,
                                                 @PathVariable("key") String key,
                                                 @RequestParam("expires") long expires,
                                                 @RequestParam(value = "credential", required = false) UUID credential,
                                                 @RequestParam(value = "signature", required = false) String signature) {
        String objectKey = stripLeadingSlash(key);
        BucketStore bucketStore = presignService.verify(PresignMethod.GET, bucketId, objectKey, expires, credential, signature);
        ObjectContent object = objectService.getObject(bucketStore, objectKey);
        return withObjectHeaders(ResponseEntity.ok(), object.metadata())
                .body(object.resource());
    }

    @SecurityRequirements
    @PutMapping("/api/v2/presigned/{bucketId}/{*key}")
    public ResponseEntity<ObjectDetails> presignedPut(@PathVariable("bucketId") UUID bucketId,
                                                      @PathVariable("key") String key,
                                                      @RequestParam("expires") long expires,
                                                      @RequestParam(value = "credential", required = false) UUID credential,
                                                      @RequestParam(value = "signature", required = false) String signature,
                                                      @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                                      InputStream body) {
        rejectFormContent(contentType);
        String objectKey = stripLeadingSlash(key);
        BucketStore bucketStore = presignService.verify(PresignMethod.PUT, bucketId, objectKey, expires, credential, signature);

        PutObjectResult result = objectService.putObject(bucketStore, objectKey, contentType, body);
        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .eTag(result.metadata().getEtag())
                .body(fileMapper.toObjectDetails(result.metadata()));
    }
}
