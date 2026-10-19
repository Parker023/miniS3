package com.anirudh.storage.minis3.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Anirudh
 * @since 19/10/26
 */
class PathEncoderTest {

    @Test
    void keepsUnreservedCharacters() {
        assertEquals("abc-XYZ_0.9~", PathEncoder.encode("abc-XYZ_0.9~"));
    }

    @Test
    void encodesSpacesAsPercent20AndReservedCharacters() {
        assertEquals("Q1%202026%20%2B%26%3F%23", PathEncoder.encode("Q1 2026 +&?#"));
    }

    @Test
    void encodesNonAsciiAsUtf8() {
        assertEquals("r%C3%A9sum%C3%A9.txt", PathEncoder.encode("résumé.txt"));
    }

    @Test
    void keyKeepsSlashesButEncodesSegments() {
        assertEquals("reports/Q1%202026/a%2Bb.txt", PathEncoder.encodeKey("reports/Q1 2026/a+b.txt"));
        assertEquals("folder/", PathEncoder.encodeKey("folder/"));
    }
}
