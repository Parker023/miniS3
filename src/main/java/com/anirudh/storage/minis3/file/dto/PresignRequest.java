package com.anirudh.storage.minis3.file.dto;

import com.anirudh.storage.minis3.constants.PresignMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author Anirudh
 * @since 19/10/26
 */
@Data
public class PresignRequest {

    @NotBlank(message = "key must not be blank")
    private String key;

    @NotNull(message = "method must be GET or PUT")
    private PresignMethod method;

    // optional: defaults to 15 minutes, at most 7 days
    private Long expiresInSeconds;
}
