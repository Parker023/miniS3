package com.anirudh.storage.minis3.file.controller;

import com.anirudh.storage.minis3.file.dto.ObjectContent;
import com.anirudh.storage.minis3.file.dto.ObjectDetails;
import com.anirudh.storage.minis3.file.dto.ObjectListResponse;
import com.anirudh.storage.minis3.file.dto.PutObjectResult;
import com.anirudh.storage.minis3.file.entity.FileMetadata;
import com.anirudh.storage.minis3.file.mapper.FileMapper;
import com.anirudh.storage.minis3.file.service.ObjectService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;

import static com.anirudh.storage.minis3.file.controller.ObjectResponses.rejectFormContent;
import static com.anirudh.storage.minis3.file.controller.ObjectResponses.stripLeadingSlash;
import static com.anirudh.storage.minis3.file.controller.ObjectResponses.withObjectHeaders;

/**
 * Objects addressed by bucket name and key, e.g. PUT /api/v2/buckets/invoices/objects/2026/42.pdf.
 * Bucket names are resolved within the caller's own buckets.
 *
 * @author Anirudh
 * @since 19/10/26
 */
@RestController
@Tag(name = "Objects (v2)", description = "Objects addressed by bucket name and key")
@RequiredArgsConstructor
@RequestMapping("/api/v2/buckets/{bucketName}/objects")
public class ObjectController {

    private final ObjectService objectService;
    private final FileMapper fileMapper;

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_READ')")
    public ResponseEntity<ObjectListResponse> listObjects(@PathVariable("bucketName") String bucketName,
                                                          @RequestParam(value = "prefix", required = false) String prefix,
                                                          @RequestParam(value = "cursor", required = false) String cursor,
                                                          @RequestParam(value = "limit", defaultValue = "" + ObjectService.DEFAULT_LIST_LIMIT) int limit) {
        return ResponseEntity.ok(objectService.listObjects(bucketName, prefix, cursor, limit));
    }

    @PutMapping("/{*key}")
    @PreAuthorize("hasAuthority('SCOPE_WRITE')")
    public ResponseEntity<ObjectDetails> putObject(@PathVariable("bucketName") String bucketName,
                                                   @PathVariable("key") String key,
                                                   @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                                   InputStream body) {
        rejectFormContent(contentType);

        PutObjectResult result = objectService.putObject(bucketName, stripLeadingSlash(key), contentType, body);
        FileMetadata metadata = result.metadata();

        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .eTag(metadata.getEtag())
                .body(fileMapper.toObjectDetails(metadata));
    }

    @GetMapping("/{*key}")
    @PreAuthorize("hasAuthority('SCOPE_READ')")
    public ResponseEntity<Resource> getObject(@PathVariable("bucketName") String bucketName,
                                              @PathVariable("key") String key) {
        ObjectContent object = objectService.getObject(bucketName, stripLeadingSlash(key));
        return withObjectHeaders(ResponseEntity.ok(), object.metadata())
                .body(object.resource());
    }

    @RequestMapping(value = "/{*key}", method = RequestMethod.HEAD)
    @PreAuthorize("hasAuthority('SCOPE_READ')")
    public ResponseEntity<Void> headObject(@PathVariable("bucketName") String bucketName,
                                           @PathVariable("key") String key) {
        FileMetadata metadata = objectService.headObject(bucketName, stripLeadingSlash(key));
        return withObjectHeaders(ResponseEntity.ok(), metadata)
                .contentLength(metadata.getSize())
                .build();
    }

    @DeleteMapping("/{*key}")
    @PreAuthorize("hasAuthority('SCOPE_DELETE')")
    public ResponseEntity<Void> deleteObject(@PathVariable("bucketName") String bucketName,
                                             @PathVariable("key") String key) {
        objectService.deleteObject(bucketName, stripLeadingSlash(key));
        return ResponseEntity.noContent().build();
    }
}
