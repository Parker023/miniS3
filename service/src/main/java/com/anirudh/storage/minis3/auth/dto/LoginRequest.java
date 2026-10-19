package com.anirudh.storage.minis3.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Anirudh
 * @since 22/02/26
 */
@Getter
@Setter
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "email must not be blank")
    @Email(message = "please check the email")
    private String email;

    @NotBlank(message = "password must not be blank")
    private String password;


}
