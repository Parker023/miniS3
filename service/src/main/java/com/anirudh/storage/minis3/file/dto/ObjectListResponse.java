package com.anirudh.storage.minis3.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * One page of a bucket listing. Pass nextCursor back as ?cursor= to get the next page.
 *
 * @author Anirudh
 * @since 19/10/26
 */
@Data
@AllArgsConstructor
public class ObjectListResponse {
    private String bucket;
    private String prefix;
    private List<ObjectDetails> objects;
    // null when this is the last page
    private String nextCursor;
    private boolean truncated;
}
