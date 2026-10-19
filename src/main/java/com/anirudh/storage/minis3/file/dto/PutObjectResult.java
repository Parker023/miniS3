package com.anirudh.storage.minis3.file.dto;

import com.anirudh.storage.minis3.file.entity.FileMetadata;

/**
 * @param created true for a new key, false when an existing object was replaced
 * @author Anirudh
 * @since 19/10/26
 */
public record PutObjectResult(FileMetadata metadata, boolean created) {
}
