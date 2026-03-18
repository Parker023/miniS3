package com.anirudh.storage.minis3.user.service;

import com.anirudh.storage.minis3.exception.UserAlreadyExistsException;
import com.anirudh.storage.minis3.user.dto.UserRegistrationRequest;
import com.anirudh.storage.minis3.user.entity.User;
import com.anirudh.storage.minis3.user.mapper.UserMapper;
import com.anirudh.storage.minis3.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * @author Anirudh
 * @since 20/02/26
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public User register(final UserRegistrationRequest requestPayload) {


        Optional<User> userByEmail = userRepository.findUserByEmail(requestPayload.getEmail());

        if (userByEmail.isPresent()) {
            log.error("Email already exists!!! ");
            throw new UserAlreadyExistsException("Email already exists - " + requestPayload.getEmail());
        }

        User user = userMapper.dtoToEntity(requestPayload);

        User savedUser = userRepository.save(user);

        log.info("{} registered with email {}", savedUser.getName(), savedUser.getEmail());

        return savedUser;

    }


}
