package com.anirudh.storage.minis3.file.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author Anirudh
 * @since 19/10/26
 */
@Data
public class ObjectDetails {
    private String key;
    private Long size;
    private String contentType;
    private String etag;
    private LocalDateTime lastModified;
}
