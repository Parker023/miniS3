package com.anirudh.storage.minis3.user.dto;

import lombok.Data;

import java.util.UUID;

/**
 * @author Anirudh
 * @since 20/02/26
 */
@Data
public class UserRegistrationResponse {
    private String name;
    private String email;
    private UUID userId;
}
