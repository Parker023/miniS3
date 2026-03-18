package com.anirudh.storage.minis3.util;

import com.anirudh.storage.minis3.auth.dto.AuthenticatedUser;
import com.anirudh.storage.minis3.exception.InvalidCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Objects;
import java.util.UUID;

/**
 * @author Anirudh
 * @since 21/02/26
 */
public class CommonUtils {

    public static UUID generateRandomUUID() {
        return UUID.randomUUID();
    }

    public static UUID getUserIdFromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (Objects.isNull(authentication) || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new InvalidCredentialsException();
        }
        return user.getUserPublicId();

    }
}
