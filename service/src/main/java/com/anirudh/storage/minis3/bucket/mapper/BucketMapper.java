package com.anirudh.storage.minis3.bucket.mapper;

import com.anirudh.storage.minis3.bucket.dto.CreateBucketRequest;
import com.anirudh.storage.minis3.bucket.dto.CreateBucketResponse;
import com.anirudh.storage.minis3.bucket.entity.BucketStore;
import com.anirudh.storage.minis3.util.CommonUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeMap;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * @author Anirudh
 * @since 24/02/26
 */
@Component
@RequiredArgsConstructor
public class BucketMapper {

    private final ModelMapper mapper;

    public BucketStore toEntity(CreateBucketRequest request, UUID ownerId) {
        BucketStore bucketStore = mapper.map(request, BucketStore.class);
        bucketStore.setOwnerId(ownerId);
        return bucketStore;
    }

    public <T> T toDto(BucketStore bucketStore, Class<T> dtoClass) {
        return mapper.map(bucketStore, dtoClass);
    }

}
