package com.anirudh.storage.minis3.client;

/**
 * 404: the bucket or object doesn't exist (bucket names are looked up among the API key owner's buckets).
 *
 * @author Anirudh
 * @since 19/10/26
 */
public class MiniS3NotFoundException extends MiniS3Exception {
    public MiniS3NotFoundException(String message) {
        super(404, message);
    }
}
