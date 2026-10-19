package com.anirudh.storage.minis3.file.dto;

import com.anirudh.storage.minis3.constants.PresignMethod;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * @author Anirudh
 * @since 19/10/26
 */
@Data
@AllArgsConstructor
public class PresignedUrlResponse {
    private String url;
    private PresignMethod method;
    private Instant expiresAt;
}
