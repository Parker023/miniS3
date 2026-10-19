package com.anirudh.storage.minis3.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author Anirudh
 * @since 02/03/26
 */
@Data
public class UploadFileRequest {

    @NotNull(message = "File cannot be null")
    private MultipartFile multipartFile;

}
