package com.anirudh.storage.minis3.file.dto;

import com.anirudh.storage.minis3.file.entity.FileMetadata;
import org.springframework.core.io.Resource;

/**
 * @author Anirudh
 * @since 19/10/26
 */
public record ObjectContent(FileMetadata metadata, Resource resource) {
}
