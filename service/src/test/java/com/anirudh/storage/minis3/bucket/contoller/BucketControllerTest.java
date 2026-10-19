package com.anirudh.storage.minis3.bucket.contoller;

import com.anirudh.storage.minis3.auth.apikey.filter.ApiKeyAuthenticationFilter;
import com.anirudh.storage.minis3.auth.jwt.JwtAuthenticationEntryPoint;
import com.anirudh.storage.minis3.auth.jwt.filters.JwtAuthenticationFilter;
import com.anirudh.storage.minis3.bucket.auth.BucketAuthService;
import com.anirudh.storage.minis3.bucket.dto.BucketDeletionResponse;
import com.anirudh.storage.minis3.bucket.dto.BucketDetails;
import com.anirudh.storage.minis3.bucket.dto.CreateBucketRequest;
import com.anirudh.storage.minis3.bucket.dto.CreateBucketResponse;
import com.anirudh.storage.minis3.bucket.entity.BucketStore;
import com.anirudh.storage.minis3.bucket.mapper.BucketMapper;
import com.anirudh.storage.minis3.bucket.service.BucketStoreService;
import com.anirudh.storage.minis3.exception.BucketAlreadyExistsException;
import com.anirudh.storage.minis3.exception.ResourceNotFoundException;
import com.anirudh.storage.minis3.util.CommonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Anirudh
 * @since 26/02/26
 */

@WebMvcTest(BucketController.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(authorities = {"SCOPE_READ", "SCOPE_WRITE", "SCOPE_DELETE"})
class BucketControllerTest {

    @MockitoBean
    private BucketStoreService bucketStoreService;
    @MockitoBean
    private BucketMapper bucketMapper;
    // named so @PreAuthorize("@bucketAuthService...") resolves to this mock
    @MockitoBean(name = "bucketAuthService")
    private BucketAuthService bucketAuthService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    UUID bucketId;
    UUID ownerId;
    private BucketStore bucketStore;

    private CreateBucketRequest createBucketRequest;
    private CreateBucketResponse createBucketResponse;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    Page<BucketStore> bucketStorePage;
    private BucketDetails bucketDetails;
    BucketDeletionResponse bucketDeletionResponse;

    @BeforeEach
    void setup() {

        ownerId = CommonUtils.generateRandomUUID();
        bucketId = CommonUtils.generateRandomUUID();

        bucketStore = new BucketStore();
        bucketStore.setBucketName("test");
        bucketStore.setBucketPublicId(bucketId);
        bucketStore.setOwnerId(ownerId);

        createBucketRequest = new CreateBucketRequest();
        createBucketRequest.setBucketName("test");

        createBucketResponse = new CreateBucketResponse();
        createBucketResponse.setMessage("Bucket created successfully");
        createBucketResponse.setBucketName("test");
        createBucketResponse.setBucketPublicId(bucketId);

        bucketDetails = new BucketDetails();
        bucketDetails.setBucketId(bucketId);
        bucketDetails.setBucketName("test");

        bucketStorePage =
                new PageImpl<>(List.of(bucketStore), PageRequest.of(0, 20), 1);
        bucketDeletionResponse = new BucketDeletionResponse();
        bucketDeletionResponse.setBucketName("test");

    }

    @Test
    void createBucket() throws Exception {

        when(bucketStoreService.createBucket(createBucketRequest)).thenReturn(bucketStore);

        when(bucketMapper.toDto(bucketStore, CreateBucketResponse.class)).thenReturn(createBucketResponse);

        mockMvc.perform((post("/api/v1/bucket/")
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                        .content(objectMapper.writeValueAsString(createBucketRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Bucket created successfully"))
                .andExpect(jsonPath("$.bucketName").value("test"));

        verify(bucketStoreService).createBucket(any());
        verify(bucketMapper).toDto(any(), any());
    }

    @Test
    void createBucket_BucketAlreadyExists() throws Exception {

        when(bucketStoreService.createBucket(createBucketRequest)).thenThrow(BucketAlreadyExistsException.class);

        mockMvc.perform((post("/api/v1/bucket/")
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                        .content(objectMapper.writeValueAsString(createBucketRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Bucket Already Exists"));

        verify(bucketStoreService).createBucket(any());
        verifyNoInteractions(bucketMapper);
    }

    @Test
    void getBucketDetails() throws Exception {

        when(bucketStoreService.getBucketStoreDetails(any()))
                .thenReturn(bucketStorePage);

        when(bucketMapper.toDto(bucketStore, BucketDetails.class))
                .thenReturn(bucketDetails);

        mockMvc.perform(get("/api/v1/bucket/")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].bucketName").value("test"))
                .andExpect(jsonPath("$.content[0].bucketId").value(bucketId.toString()));

        verify(bucketStoreService).getBucketStoreDetails(any());
    }

    @Test
    void deleteBucket() throws Exception {
        when(bucketAuthService.canAccess(bucketId)).thenReturn(true);
        when(bucketStoreService.deleteBucket(bucketId)).thenReturn(bucketStore);

        when(bucketMapper.toDto(bucketStore, BucketDeletionResponse.class)).thenReturn(bucketDeletionResponse);

        mockMvc.perform(delete("/api/v1/bucket/{bucketId}", bucketId))
                .andExpect(status().isOk());

        verify(bucketStoreService).deleteBucket(bucketId);
        verify(bucketMapper).toDto(any(), any());
    }

    @Test
    void deleteBucket_ResourceNotFound() throws Exception {
        when(bucketAuthService.canAccess(bucketId)).thenReturn(true);
        when(bucketStoreService.deleteBucket(bucketId)).thenThrow(new ResourceNotFoundException("Bucket not Found"));

        mockMvc.perform(delete("/api/v1/bucket/{bucketId}", bucketId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Bucket not Found"));

        verify(bucketStoreService).deleteBucket(bucketId);
        verifyNoInteractions(bucketMapper);
    }

    @Test
    @WithMockUser(authorities = "SCOPE_READ")
    void createBucket_withoutWriteScope_forbidden() throws Exception {
        mockMvc.perform((post("/api/v1/bucket/")
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                        .content(objectMapper.writeValueAsString(createBucketRequest)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(bucketStoreService);
    }

    @Test
    void deleteBucket_notAccessible_forbidden() throws Exception {
        when(bucketAuthService.canAccess(bucketId)).thenReturn(false);

        mockMvc.perform(delete("/api/v1/bucket/{bucketId}", bucketId))
                .andExpect(status().isForbidden());

        verifyNoInteractions(bucketStoreService);
    }
}
