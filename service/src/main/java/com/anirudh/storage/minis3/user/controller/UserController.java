package com.anirudh.storage.minis3.user.controller;

import com.anirudh.storage.minis3.user.dto.DeleteAccountRequest;
import com.anirudh.storage.minis3.user.dto.UserRegistrationRequest;
import com.anirudh.storage.minis3.user.dto.UserRegistrationResponse;
import com.anirudh.storage.minis3.user.entity.User;
import com.anirudh.storage.minis3.user.mapper.UserMapper;
import com.anirudh.storage.minis3.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * @author Anirudh
 * @since 20/02/26
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    // login token only (see SecurityConfig): an API key can't delete its owner's account
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(@RequestBody @Valid DeleteAccountRequest request) {
        userService.deleteAccount(request.getPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    public ResponseEntity<UserRegistrationResponse> registerUser(@RequestBody @Valid UserRegistrationRequest request) {
        log.info("registration request received for {}", request.getEmail());
        User registeredUser = userService.register(request);
        UserRegistrationResponse userRegistrationResponse = userMapper.entityToDTO(registeredUser);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .replacePath("/api/v1/users/{userId}")
                .buildAndExpand(userRegistrationResponse.getUserId())
                .toUri();

        return ResponseEntity.created(location)
                .body(userRegistrationResponse);
    }
}
