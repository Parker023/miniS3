package com.anirudh.storage.minis3.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;

/**
 * @author Anirudh
 * @since 11/03/26
 */
@Data
@RequiredArgsConstructor
@AllArgsConstructor
public class FileDownloadResponse {

    private Resource resource;
    private String contentType;
    private String fileName;
    private long size;
}
