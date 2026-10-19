package com.anirudh.storage.minis3.file.mapper;

import com.anirudh.storage.minis3.file.dto.ObjectDetails;
import com.anirudh.storage.minis3.file.dto.UploadFileRequest;
import com.anirudh.storage.minis3.file.entity.FileMetadata;
import com.anirudh.storage.minis3.util.CommonUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeMap;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

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

    public ObjectDetails toObjectDetails(FileMetadata fileMetadata) {
        ObjectDetails details = new ObjectDetails();
        details.setKey(fileMetadata.getFileName());
        details.setSize(fileMetadata.getSize());
        details.setContentType(fileMetadata.getContentType());
        details.setEtag(fileMetadata.getEtag());
        if (fileMetadata.getUpdatedAt() != null) {
            details.setLastModified(fileMetadata.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant());
        }
        return details;
    }
}
