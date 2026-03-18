package com.anirudh.storage.minis3.auth.service;

import com.anirudh.storage.minis3.auth.dto.LoginRequest;
import com.anirudh.storage.minis3.auth.dto.LoginResponse;
import com.anirudh.storage.minis3.auth.jwt.service.JwtService;
import com.anirudh.storage.minis3.exception.InvalidCredentialsException;
import com.anirudh.storage.minis3.user.entity.User;
import com.anirudh.storage.minis3.user.repository.UserRepository;
import com.anirudh.storage.minis3.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * @author Anirudh
 * @since 22/02/26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordUtil passwordUtil;
    private final JwtService jwtService;


    public LoginResponse login(LoginRequest loginRequest) {

        Optional<User> userByEmail = userRepository.findUserByEmail(loginRequest.getEmail());

        if (userByEmail.isEmpty()) {
            throw new InvalidCredentialsException();
        }
        User user = userByEmail.get();

        if (!passwordUtil.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        log.info("user {} authenticated", user.getName());

        String token = jwtService.generateToken(String.valueOf(user.getUserPublicId()));
        return prepareLoginResponse(user, token);
    }

    private LoginResponse prepareLoginResponse(User user, String token) {
        return LoginResponse
                .builder()
                .name(user.getName())
                .message("Logged in successfully")
                .token(token)
                .build();
    }
}
