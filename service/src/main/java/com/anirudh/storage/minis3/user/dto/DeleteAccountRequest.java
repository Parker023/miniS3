package com.anirudh.storage.minis3.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author Anirudh
 * @since 19/10/26
 */
@Data
public class DeleteAccountRequest {

    // re-confirm the password so a stolen or forgotten-open token alone can't wipe the account
    @NotBlank(message = "password must not be blank")
    private String password;
}
