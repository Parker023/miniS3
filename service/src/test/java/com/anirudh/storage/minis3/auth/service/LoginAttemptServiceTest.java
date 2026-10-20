package com.anirudh.storage.minis3.auth.service;

import com.anirudh.storage.minis3.configuration.MiniS3LimitsProperties;
import com.anirudh.storage.minis3.exception.TooManyLoginAttemptsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Anirudh
 * @since 19/10/26
 */
class LoginAttemptServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-19T10:00:00Z"));
    private LoginAttemptService service;

    @BeforeEach
    void setup() {
        MiniS3LimitsProperties limits = new MiniS3LimitsProperties();
        limits.setLoginMaxFailures(3);
        limits.setLoginLockout(Duration.ofMinutes(15));
        service = new LoginAttemptService(limits, clock);
    }

    @Test
    void locksAfterMaxFailuresUntilWindowEnds() {
        for (int i = 0; i < 3; i++) {
            service.checkAllowed("a@example.com");
            service.recordFailure("a@example.com");
        }

        TooManyLoginAttemptsException locked = assertThrows(TooManyLoginAttemptsException.class,
                () -> service.checkAllowed("a@example.com"));
        assertEquals(Duration.ofMinutes(15), locked.getRetryAfter());

        clock.advance(Duration.ofMinutes(14));
        assertThrows(TooManyLoginAttemptsException.class, () -> service.checkAllowed("a@example.com"));

        clock.advance(Duration.ofMinutes(1));
        assertDoesNotThrow(() -> service.checkAllowed("a@example.com"));
    }

    @Test
    void emailIsCaseInsensitiveAndOtherEmailsUnaffected() {
        for (int i = 0; i < 3; i++) {
            service.recordFailure(i % 2 == 0 ? "A@Example.com" : " a@example.com ");
        }
        assertThrows(TooManyLoginAttemptsException.class, () -> service.checkAllowed("a@example.com"));
        assertDoesNotThrow(() -> service.checkAllowed("b@example.com"));
    }

    @Test
    void successResetsAndOldFailuresExpire() {
        service.recordFailure("a@example.com");
        service.recordFailure("a@example.com");
        service.recordSuccess("a@example.com");
        service.recordFailure("a@example.com");
        assertDoesNotThrow(() -> service.checkAllowed("a@example.com"));

        // failures spread over more than one window never add up to a lockout
        service.recordFailure("c@example.com");
        service.recordFailure("c@example.com");
        clock.advance(Duration.ofMinutes(16));
        service.recordFailure("c@example.com");
        assertDoesNotThrow(() -> service.checkAllowed("c@example.com"));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
