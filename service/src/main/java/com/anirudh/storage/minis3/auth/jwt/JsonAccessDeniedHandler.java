package com.anirudh.storage.minis3.auth.jwt;

import com.anirudh.storage.minis3.exception.dto.ErrorResponse;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Writes 403 for URL-level denials (e.g. an API key calling /api/v1/keys) directly, like the
 * GlobalExceptionHandler does for @PreAuthorize denials.
 * <p>
 * The default handler calls sendError(403), which on a real servlet container forwards to /error; that
 * forward carries no credentials and was itself rejected, so clients saw 401 instead of 403.
 *
 * @author Anirudh
 * @since 19/10/26
 */
@Component
@RequiredArgsConstructor
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse errorResponse = ErrorResponse.builder()
                .message("Access Denied")
                .timestamp(LocalDateTime.now())
                .build();
        ServletOutputStream outputStream = response.getOutputStream();
        outputStream.write(objectMapper.writeValueAsString(errorResponse).getBytes());
        outputStream.flush();
    }
}
