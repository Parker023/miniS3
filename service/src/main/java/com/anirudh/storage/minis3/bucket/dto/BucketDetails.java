package com.anirudh.storage.minis3.bucket.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @author Anirudh
 * @since 25/02/26
 */
@Data
public class BucketDetails {
    private String bucketName;
    private UUID bucketId;
    private LocalDateTime createdAt;

}
