package com.anirudh.storage.minis3.auth.controller;

import com.anirudh.storage.minis3.auth.dto.LoginRequest;
import com.anirudh.storage.minis3.auth.dto.LoginResponse;
import com.anirudh.storage.minis3.auth.apikey.filter.ApiKeyAuthenticationFilter;
import com.anirudh.storage.minis3.auth.jwt.JwtAuthenticationEntryPoint;
import com.anirudh.storage.minis3.auth.jwt.filters.JwtAuthenticationFilter;
import com.anirudh.storage.minis3.auth.service.AuthService;
import com.anirudh.storage.minis3.exception.InvalidCredentialsException;
import com.anirudh.storage.minis3.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Anirudh
 * @since 23/02/26
 */

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;


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
    void login_user_200() throws Exception {
        when(authService.login(any())).thenReturn(loginResponse);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(header().exists("Authorization"))
                .andExpect(jsonPath("$.token").value("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbWlydWRoc2hhbm11a2hhMjM5OUBnbWFpbC5jb20iLCJpYXQiOjE3NzE4OTg1OTQsImV4cCI6MTc3MTg5OTQ5NH0.YJD6nxvd4jnDpBvWo72k9NntGRzgAe-8qnzyL15rrD8"))
                .andExpect(jsonPath("$.message").value("Logged in successfully"));
        verify(authService).login(any());
    }

    @Test
    void login_user_401() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Authorization"))
                .andExpect(jsonPath("$.message").value("Invalid Credentials"));
        verify(authService).login(any());
    }


}