package com.team03.ticketmon._global.service;

import com.team03.ticketmon._global.dto.UploadResponseDTO;
import com.team03.ticketmon._global.util.FileUtil; // FileUtil 임포트
import com.team03.ticketmon._global.util.uploader.StorageUploader;
import com.team03.ticketmon._global.util.StoragePathProvider; // StoragePathProvider 임포트
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ExampleProfileImageService {

    private final StorageUploader storageUploader;
    private final StoragePathProvider storagePathProvider; // StoragePathProvider 주입

    public UploadResponseDTO uploadProfileImage(String uuid, MultipartFile file) {
        String fileExtension = FileUtil.getExtensionFromMimeType(Objects.requireNonNull(file.getContentType()));
        String path = storagePathProvider.getProfilePath(uuid, fileExtension); // StoragePathProvider에서 경로 생성
        String bucket = storagePathProvider.getProfileBucketName(); // StoragePathProvider에서 버킷 이름 가져오기
        String url = storageUploader.uploadFile(file, bucket, path);
        return new UploadResponseDTO(file.getOriginalFilename(), url);
    }
}