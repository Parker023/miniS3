package com.anirudh.storage.minis3.user.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * @author Anirudh
 * @since 20/02/26
 */
@Data
public class UserRegistrationRequest {

    @NotBlank(message = "name should not be empty or null")
    @Size(min = 4, max = 50)
    private String name;

    @Email(message = "please check the email")
    @NotBlank(message = "email should not be null or empty")

    private String email;

    @NotBlank(message = "password should not be null or empty")
    @Size(min = 8, max = 25)
    private String password;

}
