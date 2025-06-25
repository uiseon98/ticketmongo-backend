package com.team03.ticketmon._global.service;

import com.team03.ticketmon._global.dto.UploadResponseDTO;
import com.team03.ticketmon._global.util.uploader.StorageUploader;
import com.team03.ticketmon._global.util.FileValidator; // FileValidator 임포트 추가
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class TestUploadService {

    private final StorageUploader storageUploader;

    public UploadResponseDTO uploadTestFile(MultipartFile file) {
        // 파일 유효성 검사 추가: 파일 업로드 전에 FileValidator를 호출하여 유효성 검사(크기, 형식)를 수행합니다.
        // 이는 FileValidator 적용 예시를 제공하고, 누락될 수 있는 검증을 보완합니다.
        FileValidator.validate(file);

        // 테스트용 버킷/경로 하드코딩 (후에 동적으로 바꿔도 됨)
        String bucket = "ticketmon-dev-profile-imgs";
//        String path = "test/" + file.getOriginalFilename();
        String path = "test";

        // SupabaseUploader → 실제 업로드
        String uploadedUrl = storageUploader.uploadFile(file, bucket, path);

        // 클라이언트에 업로드된 파일명 + 공개 URL 반환
        return new UploadResponseDTO(file.getOriginalFilename(), uploadedUrl);
    }
}
