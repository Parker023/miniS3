package com.anirudh.storage.minis3.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * @author Anirudh
 * @since 25/02/26
 */
@Data
@AllArgsConstructor
public class AuthenticatedUser {
    private UUID userPublicId;
}
