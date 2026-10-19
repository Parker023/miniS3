package com.anirudh.storage.minis3.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * @author Anirudh
 * @since 22/02/26
 */
@Data
@Builder
@AllArgsConstructor
public class LoginResponse {
    private String name;
    private String message;
    private String token;
}
