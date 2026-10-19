package com.anirudh.storage.minis3.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * A downloaded object: its metadata plus a stream of its content, read straight from the connection.
 * Always close it (try-with-resources) so the connection is released.
 *
 * @author Anirudh
 * @since 19/10/26
 */
public final class MiniS3Object implements AutoCloseable {

    private final ObjectInfo info;
    private final InputStream content;

    MiniS3Object(ObjectInfo info, InputStream content) {
        this.info = info;
        this.content = content;
    }

    public ObjectInfo info() {
        return info;
    }

    public InputStream content() {
        return content;
    }

    /**
     * Reads the whole content into memory and closes the stream.
     */
    public byte[] readAllBytes() {
        try (InputStream in = content) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read object " + info.key(), e);
        }
    }

    @Override
    public void close() {
        try {
            content.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
