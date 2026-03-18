package com.anirudh.storage.minis3.bucket.entity;

import com.anirudh.storage.minis3.entity.BaseEntity;
import com.anirudh.storage.minis3.util.CommonUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * @author Anirudh
 * @since 24/02/26
 */
@Entity
@Getter
@Setter
public class BucketStore extends BaseEntity {

    public BucketStore() {
        this.bucketPublicId = CommonUtils.generateRandomUUID();
    }

    @Column(nullable = false)
    private String bucketName;

    @Column(nullable = false)
    private UUID ownerId;


    @Column(nullable = false, unique = true)
    private UUID bucketPublicId;

}
