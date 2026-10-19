package com.anirudh.storage.minis3.client;

import java.net.URI;
import java.time.Instant;

/**
 * A time-limited link for one object and one method that needs no Authorization header.
 *
 * @author Anirudh
 * @since 19/10/26
 */
public record PresignedUrl(URI url, String method, Instant expiresAt) {
}
