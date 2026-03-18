package com.anirudh.storage.minis3.file.dto;

import lombok.Data;

import java.util.UUID;

/**
 * @author Anirudh
 * @since 02/03/26
 */
@Data
public class FileDetailsResponse {
    private String fileName;
    private Long size;
    private String contentType;
    private UUID filePublicId;
}
