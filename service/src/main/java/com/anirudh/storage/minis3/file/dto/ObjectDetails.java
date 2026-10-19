package com.anirudh.storage.minis3.file.dto;

import lombok.Data;

import java.time.Instant;

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
    // UTC instant, e.g. 2026-10-19T10:00:00Z, so clients in any time zone read the same moment
    private Instant lastModified;
}
