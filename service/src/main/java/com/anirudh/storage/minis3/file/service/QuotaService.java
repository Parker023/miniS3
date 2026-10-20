package com.anirudh.storage.minis3.file.service;

import com.anirudh.storage.minis3.configuration.MiniS3LimitsProperties;
import com.anirudh.storage.minis3.exception.QuotaExceededException;
import com.anirudh.storage.minis3.file.repository.FileMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Per-user limit on total stored bytes (minis3.limits.user-quota), across all of the user's buckets.
 *
 * @author Anirudh
 * @since 19/10/26
 */
@Service
@RequiredArgsConstructor
public class QuotaService {

    private final FileMetadataRepository fileMetadataRepository;
    private final MiniS3LimitsProperties limits;

    /**
     * @param deltaBytes how much the owner's usage would grow (negative when a replacement is smaller)
     * @throws QuotaExceededException if usage + delta would go over the quota
     */
    public void ensureFits(UUID ownerId, long deltaBytes) {
        long quota = limits.getUserQuota().toBytes();
        if (quota <= 0) {
            return;
        }
        long usage = fileMetadataRepository.totalSizeByOwner(ownerId);
        if (usage + deltaBytes > quota) {
            throw new QuotaExceededException("Storage quota exceeded: using " + usage + " of " + quota + " bytes");
        }
    }
}
