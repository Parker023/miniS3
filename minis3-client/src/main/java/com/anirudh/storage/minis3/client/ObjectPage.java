package com.anirudh.storage.minis3.client;

import java.util.List;

/**
 * One page of a listing. Pass {@link #nextCursor()} to the next {@code listObjects} call while {@link #truncated()}.
 *
 * @author Anirudh
 * @since 19/10/26
 */
public record ObjectPage(String bucket, String prefix, List<ObjectInfo> objects, String nextCursor, boolean truncated) {
}
