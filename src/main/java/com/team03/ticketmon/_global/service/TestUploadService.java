package com.team03.ticketmon._global.service;

import com.team03.ticketmon._global.dto.UploadResponseDTO;
import com.team03.ticketmon._global.util.uploader.StorageUploader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class TestUploadService {

    private final StorageUploader storageUploader;

    public UploadResponseDTO uploadTestFile(MultipartFile file) {
        // TODO: 경로와 버킷 이름은 임시로 하드코딩 (후에 개선)
        String bucket = "ticketmon-dev-profile-imgs";
        String path = "test/" + file.getOriginalFilename();

        String uploadedUrl = storageUploader.uploadFile(file, bucket, path);
        return new UploadResponseDTO(file.getOriginalFilename(), uploadedUrl);
    }
}
