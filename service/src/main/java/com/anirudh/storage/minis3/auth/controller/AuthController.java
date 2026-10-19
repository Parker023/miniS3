package com.anirudh.storage.minis3.auth.controller;

import com.anirudh.storage.minis3.auth.dto.LoginRequest;
import com.anirudh.storage.minis3.auth.dto.LoginResponse;
import com.anirudh.storage.minis3.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.anirudh.storage.minis3.constants.MiniS3Status.BEARER;

/**
 * @author Anirudh
 * @since 22/02/26
 */

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest loginRequest) {
        log.info("{} made a login request", loginRequest.getEmail());
        LoginResponse response = authService.login(loginRequest);
        return ResponseEntity
                .ok()
                .header(HttpHeaders.AUTHORIZATION, BEARER.getValue() + response.getToken())
                .body(response);
    }
}
