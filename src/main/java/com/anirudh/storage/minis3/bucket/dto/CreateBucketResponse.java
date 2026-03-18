package com.anirudh.storage.minis3.bucket.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * @author Anirudh
 * @since 24/02/26
 */
@Data
public class CreateBucketResponse {
    private String message;
    private String bucketName;
    private UUID bucketPublicId;
}
