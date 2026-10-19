package com.anirudh.storage.minis3.client;

import java.time.Instant;

/**
 * Metadata of a stored object.
 *
 * @param key          the object key, e.g. {@code invoices/2026/42.pdf}
 * @param size         size in bytes
 * @param contentType  the Content-Type it was uploaded with
 * @param etag         hex MD5 of the content, without quotes; null for files uploaded through the v1 API
 * @param lastModified when the object was last written
 * @author Anirudh
 * @since 19/10/26
 */
public record ObjectInfo(String key, long size, String contentType, String etag, Instant lastModified) {
}
