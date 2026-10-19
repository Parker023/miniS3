package com.anirudh.storage.minis3.bucket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @author Anirudh
 * @since 24/02/26
 */
@Data
public class CreateBucketRequest {
    @NotBlank(message = "bucket name cannot be blank")
    @Size(min = 3, max = 20)
    // bucket names appear in v2 URL paths, so no slashes or spaces
    @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "bucket name may only contain letters, digits, '.', '_' and '-'")
    private String bucketName;
}
