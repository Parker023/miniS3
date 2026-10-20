package com.anirudh.storage.minis3.user.service;

import com.anirudh.storage.minis3.exception.UserAlreadyExistsException;
import com.anirudh.storage.minis3.user.dto.UserRegistrationRequest;
import com.anirudh.storage.minis3.user.entity.User;
import com.anirudh.storage.minis3.user.mapper.UserMapper;
import com.anirudh.storage.minis3.user.repository.UserRepository;
import com.anirudh.storage.minis3.util.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.anirudh.storage.minis3.configuration.MiniS3LimitsProperties;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Anirudh
 * @since 21/02/26
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordUtil passwordUtil;

    // real defaults: no invite code required
    @Spy
    private MiniS3LimitsProperties limits = new MiniS3LimitsProperties();

    @InjectMocks
    private UserService userService;

    private UserRegistrationRequest request;

    private User user;

    @BeforeEach
    public void setup() {
        request = new UserRegistrationRequest();
        request.setPassword("myPass");
        request.setName("testUser");
        request.setEmail("abc23@gmail.com");

        user = new User();
        user.setEmail("abc23@gmail.com");
        user.setName("testUser");
    }


    @Test
    void register_shouldSaveAndReturnUser() {

        when(userMapper.dtoToEntity(request)).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);
        when(userRepository.findUserByEmail(request.getEmail())).thenReturn(Optional.empty());

        User registered = userService.register(request);

        verify(userMapper).dtoToEntity(request);
        verify(userRepository).save(user);

        assertNotNull(registered);
        assertEquals(user.getEmail(), registered.getEmail());
        assertEquals(user.getName(), registered.getName());
    }

    @Test
    void email_exists() {

        when(userRepository.findUserByEmail(request.getEmail())).thenReturn(Optional.of(user));

        assertThrows(
                UserAlreadyExistsException.class,
                () -> userService.register(request)
        );

        verify(userMapper, never()).dtoToEntity(request);
        verify(userRepository).findUserByEmail(anyString());
        verify(userRepository, never()).save(any());


    }
}