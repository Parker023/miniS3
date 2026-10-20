package com.anirudh.storage.minis3.user.service;

import com.anirudh.storage.minis3.auth.apikey.repository.ApiKeyRepository;
import com.anirudh.storage.minis3.bucket.entity.BucketStore;
import com.anirudh.storage.minis3.bucket.repository.BucketStoreRepository;
import com.anirudh.storage.minis3.bucket.service.BucketStoreService;
import com.anirudh.storage.minis3.configuration.MiniS3LimitsProperties;
import com.anirudh.storage.minis3.exception.InvalidCredentialsException;
import com.anirudh.storage.minis3.exception.InvalidInviteCodeException;
import com.anirudh.storage.minis3.exception.UserAlreadyExistsException;
import com.anirudh.storage.minis3.user.dto.UserRegistrationRequest;
import com.anirudh.storage.minis3.user.entity.User;
import com.anirudh.storage.minis3.user.mapper.UserMapper;
import com.anirudh.storage.minis3.user.repository.UserRepository;
import com.anirudh.storage.minis3.util.CommonUtils;
import com.anirudh.storage.minis3.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    private final PasswordUtil passwordUtil;
    private final BucketStoreRepository bucketStoreRepository;
    private final BucketStoreService bucketStoreService;
    private final ApiKeyRepository apiKeyRepository;
    private final MiniS3LimitsProperties limits;

    public User register(final UserRegistrationRequest requestPayload) {
        checkInviteCode(requestPayload.getInviteCode());

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

    private void checkInviteCode(String provided) {
        String required = limits.getInviteCode();
        if (required == null || required.isBlank()) {
            return;
        }
        if (provided == null || !MessageDigest.isEqual(
                required.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) {
            log.warn("registration rejected: missing or wrong invite code");
            throw new InvalidInviteCodeException();
        }
    }

    /**
     * Deletes the logged-in user with all their buckets (metadata now, bytes on disk after commit) and API keys.
     * Login tokens already issued stay valid until they expire, but no longer own anything.
     */
    @Transactional
    public void deleteAccount(String password) {
        UUID userId = CommonUtils.getUserIdFromSecurityContext();
        User user = userRepository.findByUserPublicId(userId).orElseThrow(InvalidCredentialsException::new);
        if (!passwordUtil.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        List<BucketStore> buckets = bucketStoreRepository.findAllByOwnerId(userId);
        buckets.forEach(bucketStoreService::deleteWithContents);
        long keys = apiKeyRepository.deleteByOwnerId(userId);
        userRepository.delete(user);

        log.info("account {} deleted with {} buckets and {} api keys", userId, buckets.size(), keys);
    }
}
