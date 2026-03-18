package com.anirudh.storage.minis3.user.controller;

import com.anirudh.storage.minis3.auth.jwt.JwtAuthenticationEntryPoint;
import com.anirudh.storage.minis3.auth.jwt.filters.JwtAuthenticationFilter;
import com.anirudh.storage.minis3.exception.UserAlreadyExistsException;
import com.anirudh.storage.minis3.user.dto.UserRegistrationRequest;
import com.anirudh.storage.minis3.user.dto.UserRegistrationResponse;
import com.anirudh.storage.minis3.user.entity.User;
import com.anirudh.storage.minis3.user.mapper.UserMapper;
import com.anirudh.storage.minis3.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * @author Anirudh
 * @since 21/02/26
 */
@WebMvcTest(value = UserController.class
        , excludeAutoConfiguration = {DataJpaRepositoriesAutoConfiguration.class
        , HibernateJpaAutoConfiguration.class})
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private UserMapper userMapper;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;


    private UserRegistrationRequest request;
    private User user;
    private UserRegistrationResponse response;

    @BeforeEach
    void setup() {

        request = new UserRegistrationRequest();
        request.setName("testUser");
        request.setEmail("abc@gmail.com");
        request.setPassword("pass23444");

        user = new User();
        user.setName("testUser");
        user.setEmail("abc@gmail.com");
        user.setUserPublicId(UUID.randomUUID());

        response = new UserRegistrationResponse();
        response.setName("testUser");
        response.setEmail("abc@gmail.com");
        response.setUserId(user.getUserPublicId());
    }

    @Test
    void registerUser_shouldReturn201AndLocationHeader() throws Exception {
        when(userService.register(any())).thenReturn(user);

        when(userMapper.entityToDTO(user)).thenReturn(response);

        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("testUser"))
                .andExpect(jsonPath("$.email").value("abc@gmail.com"))
                .andExpect(jsonPath("$.userId").value(response.getUserId().toString()));

        verify(userService).register(any());
        verify(userMapper).entityToDTO(user);
    }

    @Test
    void registerUser_shouldReturn400() throws Exception {
        UserRegistrationRequest req = new UserRegistrationRequest();
        req.setName("testUser");
        req.setPassword("pass");
        req.setEmail("");
        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        verify(userService, never()).register(any());

    }

    @Test
    void email_exists_returns_409() throws Exception {

        when(userService.register(request))
                .thenThrow(new UserAlreadyExistsException("Email already exists - " + "abc@gmail.com"));

        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already exists - " + "abc@gmail.com"));
        verify(userService).register(request);
        verify(userMapper, never()).entityToDTO(any());
    }

    @Test
    void malformed_payload() throws Exception {

        String malformed_json = "{\"email\":}";

        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformed_json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request"));
        verifyNoInteractions(userService);
        verify(userMapper, never()).entityToDTO(any());
    }

}