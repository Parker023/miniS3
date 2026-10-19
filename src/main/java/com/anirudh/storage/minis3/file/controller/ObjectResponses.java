package com.anirudh.storage.minis3.file.controller;

import com.anirudh.storage.minis3.exception.InvalidRequestException;
import com.anirudh.storage.minis3.file.entity.FileMetadata;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.ZoneId;

/**
 * Response helpers shared by the authenticated and pre-signed object endpoints.
 *
 * @author Anirudh
 * @since 19/10/26
 */
final class ObjectResponses {

    private ObjectResponses() {
    }

    /**
     * Content-Type, ETag and Last-Modified. With ETag/Last-Modified set, Spring answers
     * If-None-Match / If-Modified-Since with 304 and Range requests with 206 on its own.
     */
    static ResponseEntity.BodyBuilder withObjectHeaders(ResponseEntity.BodyBuilder builder, FileMetadata metadata) {
        builder.contentType(MediaType.parseMediaType(metadata.getContentType() == null
                        ? MediaType.APPLICATION_OCTET_STREAM_VALUE : metadata.getContentType()))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes");
        if (metadata.getEtag() != null) {
            builder.eTag(metadata.getEtag());
        }
        if (metadata.getUpdatedAt() != null) {
            builder.lastModified(metadata.getUpdatedAt().atZone(ZoneId.systemDefault()));
        }
        return builder;
    }

    // Spring's FormContentFilter consumes form bodies on PUT, which would silently store an empty object
    static void rejectFormContent(String contentType) {
        if (contentType != null && contentType.startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE)) {
            throw new InvalidRequestException("Content-Type " + MediaType.APPLICATION_FORM_URLENCODED_VALUE + " is not supported for objects");
        }
    }

    // {*key} captures the rest of the path including slashes, with a leading '/'
    static String stripLeadingSlash(String key) {
        return key.startsWith("/") ? key.substring(1) : key;
    }
}
