package com.anirudh.storage.minis3.file.entity;

import com.anirudh.storage.minis3.bucket.entity.BucketStore;
import com.anirudh.storage.minis3.entity.BaseEntity;
import com.anirudh.storage.minis3.util.CommonUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * @author Anirudh
 * @since 02/03/26
 */
@Entity
@Getter
@Setter
@Table(name = "file_metadata",
        uniqueConstraints = {@UniqueConstraint(name = "uk_bucket_file_name", columnNames = {"bucket_id", "file_name"})})
public class FileMetadata extends BaseEntity {

    public FileMetadata() {
        this.filePublicId = CommonUtils.generateRandomUUID();
    }

    @Column(nullable = false, unique = true)
    private UUID filePublicId;
    @Column(nullable = false)
    private String fileName;
    @Column(nullable = false)
    private Long size;
    private String contentType;
    // hex MD5 of the content; set by v2 uploads, null for files uploaded through v1
    private String etag;
    @ManyToOne
    @JoinColumn(name = "bucket_id", nullable = false)
    private BucketStore bucketStore;
}
