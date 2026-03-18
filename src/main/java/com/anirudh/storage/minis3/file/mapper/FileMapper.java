package com.anirudh.storage.minis3.file.mapper;

import com.anirudh.storage.minis3.file.dto.UploadFileRequest;
import com.anirudh.storage.minis3.file.entity.FileMetadata;
import com.anirudh.storage.minis3.util.CommonUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeMap;
import org.springframework.stereotype.Component;

/**
 * @author Anirudh
 * @since 02/03/26
 */
@Component
@RequiredArgsConstructor
public class FileMapper {

    private final ModelMapper modelMapper;

    public FileMetadata toEntity(UploadFileRequest uploadFileRequest) {
        FileMetadata mapped = modelMapper.map(uploadFileRequest, FileMetadata.class);
        mapped.setSize(uploadFileRequest.getMultipartFile().getSize());
        mapped.setFileName(uploadFileRequest.getMultipartFile().getOriginalFilename());
        mapped.setContentType(uploadFileRequest.getMultipartFile().getContentType());
        return mapped;
    }

    public <T> T toDto(FileMetadata fileMetadata, Class<T> classType) {
        return modelMapper.map(fileMetadata, classType);
    }
}
