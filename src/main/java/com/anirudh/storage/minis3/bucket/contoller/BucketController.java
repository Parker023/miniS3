package com.anirudh.storage.minis3.bucket.contoller;

import com.anirudh.storage.minis3.bucket.auth.BucketAuthService;
import com.anirudh.storage.minis3.bucket.dto.BucketDeletionResponse;
import com.anirudh.storage.minis3.bucket.dto.BucketDetails;
import com.anirudh.storage.minis3.bucket.dto.CreateBucketRequest;
import com.anirudh.storage.minis3.bucket.dto.CreateBucketResponse;
import com.anirudh.storage.minis3.bucket.entity.BucketStore;
import com.anirudh.storage.minis3.bucket.mapper.BucketMapper;
import com.anirudh.storage.minis3.bucket.service.BucketStoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * @author Anirudh
 * @since 24/02/26
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/bucket/")
public class BucketController {

    private final BucketStoreService bucketStoreService;
    private final BucketMapper bucketMapper;
    private final BucketAuthService bucketAuthService;

    @PostMapping
    public ResponseEntity<CreateBucketResponse> createBucket(@RequestBody @Valid CreateBucketRequest createBucketRequest) {
        BucketStore bucketStore = bucketStoreService.createBucket(createBucketRequest);

        CreateBucketResponse bucketResponse = bucketMapper.toDto(bucketStore, CreateBucketResponse.class);

        bucketResponse.setMessage("Bucket created successfully");
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .replacePath("/api/v1/bucket/{bucketPublicId}")
                .buildAndExpand(bucketResponse.getBucketPublicId())
                .toUri();

        return ResponseEntity
                .created(location)
                .body(bucketResponse);
    }

    @GetMapping
    public ResponseEntity<Page<BucketDetails>> getBucketDetails(@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<BucketStore> bucketStorePage = bucketStoreService.getBucketStoreDetails(pageable);
        Page<BucketDetails> bucketDetailsPage = bucketStorePage.map(bucketStore -> bucketMapper.toDto(bucketStore, BucketDetails.class));
        return ResponseEntity.ok(bucketDetailsPage);

    }

    @DeleteMapping("/{bucketId}")
    @PreAuthorize("@bucketAuthService.isOwner(#bucketId)")
    public ResponseEntity<BucketDeletionResponse> deleteBucket(@PathVariable("bucketId") UUID bucketId) {
        BucketStore bucketStore = bucketStoreService.deleteBucket(bucketId);

        BucketDeletionResponse deletionResponse = bucketMapper.toDto(bucketStore, BucketDeletionResponse.class);

        return ResponseEntity.ok(deletionResponse);

    }


}
