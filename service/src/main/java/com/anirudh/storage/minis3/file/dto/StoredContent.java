package com.anirudh.storage.minis3.file.dto;

/**
 * What the storage backend wrote: byte count and hex MD5 of the content.
 *
 * @author Anirudh
 * @since 19/10/26
 */
public record StoredContent(long size, String md5) {
}
