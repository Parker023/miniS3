package com.anirudh.storage.minis3.auth.service;

import com.anirudh.storage.minis3.configuration.MiniS3LimitsProperties;
import com.anirudh.storage.minis3.exception.TooManyLoginAttemptsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Locks an email out after too many failed logins within a window, to stop password guessing.
 * <p>
 * Kept in memory: a restart clears it, and it is per instance. Keyed by email rather than client IP because
 * behind Cloudflare Tunnel every request arrives from localhost; add a Cloudflare rate-limiting rule for
 * per-IP limits.
 *
 * @author Anirudh
 * @since 19/10/26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    // bound memory if someone sprays random emails; expired entries are dropped first
    private static final int MAX_TRACKED = 10_000;

    private final MiniS3LimitsProperties limits;
    private final Clock clock;
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();

    private record Failures(int count, Instant windowStart) {
    }

    /**
     * @throws TooManyLoginAttemptsException while the email is locked out
     */
    public void checkAllowed(String email) {
        String key = key(email);
        Failures current = failures.get(key);
        if (current == null) {
            return;
        }
        if (expired(current)) {
            failures.remove(key, current);
            return;
        }
        if (current.count() >= limits.getLoginMaxFailures()) {
            Duration retryAfter = Duration.between(clock.instant(), current.windowStart().plus(limits.getLoginLockout()));
            throw new TooManyLoginAttemptsException(retryAfter.isNegative() ? Duration.ZERO : retryAfter);
        }
    }

    public void recordFailure(String email) {
        if (failures.size() >= MAX_TRACKED) {
            failures.values().removeIf(this::expired);
        }
        Failures updated = failures.merge(key(email), new Failures(1, clock.instant()),
                (old, first) -> expired(old) ? first : new Failures(old.count() + 1, old.windowStart()));
        if (updated.count() == limits.getLoginMaxFailures()) {
            log.warn("login locked for {} after {} failed attempts", email, updated.count());
        }
    }

    public void recordSuccess(String email) {
        failures.remove(key(email));
    }

    private boolean expired(Failures entry) {
        return !clock.instant().isBefore(entry.windowStart.plus(limits.getLoginLockout()));
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
