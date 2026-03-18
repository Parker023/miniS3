package com.anirudh.storage.minis3.auth.service;

import com.anirudh.storage.minis3.auth.dto.LoginRequest;
import com.anirudh.storage.minis3.auth.dto.LoginResponse;
import com.anirudh.storage.minis3.auth.jwt.service.JwtService;
import com.anirudh.storage.minis3.exception.InvalidCredentialsException;
import com.anirudh.storage.minis3.user.entity.User;
import com.anirudh.storage.minis3.user.repository.UserRepository;
import com.anirudh.storage.minis3.util.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Anirudh
 * @since 23/02/26
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordUtil passwordUtil;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private LoginRequest loginRequest;
    private LoginResponse loginResponse;
    private User user;

    @BeforeEach
    void setup() {
        loginRequest = new LoginRequest("abc23@gmail.com", "test239912");
        loginResponse = new LoginResponse("testname", "Logged in successfully", "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbWlydWRoc2hhbm11a2hhMjM5OUBnbWFpbC5jb20iLCJpYXQiOjE3NzE4OTg1OTQsImV4cCI6MTc3MTg5OTQ5NH0.YJD6nxvd4jnDpBvWo72k9NntGRzgAe-8qnzyL15rrD8");
        user = new User();
        user.setName("testname");
        user.setEmail("abc23@gmail.com");
        user.setPassword("$2a$10$2sOUsJFlZs17o.OGIsn4SuuAe66wpITo9QgNDquqG1tqolNL5w932");
    }


    @Test
    void validateUserForLogin_200() {
        when(userRepository.findUserByEmail(anyString())).thenReturn(Optional.of(user));

        when(passwordUtil.matches(anyString(), anyString())).thenReturn(true);
        when(jwtService.generateToken(anyString())).thenReturn("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbWlydWRoc2hhbm11a2hhMjM5OUBnbWFpbC5jb20iLCJpYXQiOjE3NzE4OTg1OTQsImV4cCI6MTc3MTg5OTQ5NH0.YJD6nxvd4jnDpBvWo72k9NntGRzgAe-8qnzyL15rrD8");

        LoginResponse response = authService.login(loginRequest);
        assertEquals("Logged in successfully", response.getMessage());

        assertEquals(loginResponse.getToken(), response.getToken());
        verify(userRepository).findUserByEmail(anyString());

        verify(passwordUtil).matches(anyString(), anyString());
        verify(jwtService).generateToken(anyString());
    }

    @Test
    void validateUserForLogin_401() {
        when(userRepository.findUserByEmail(anyString())).thenReturn(Optional.empty());
        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(loginRequest));

        verify(userRepository).findUserByEmail(anyString());

        verify(passwordUtil, never()).matches(anyString(), anyString());

        verify(jwtService, never()).generateToken(anyString());
    }

    @Test
    void validateUserForLogin_401_password_match() {
        when(userRepository.findUserByEmail(anyString())).thenReturn(Optional.of(user));
        when(passwordUtil.matches(anyString(), anyString())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(loginRequest));

        verify(userRepository).findUserByEmail(anyString());
        verify(passwordUtil).matches(anyString(), anyString());
        verify(jwtService, never()).generateToken(anyString());
    }


}