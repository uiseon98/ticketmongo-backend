package com.team03.ticketmon._global.util;

import java.util.Optional;

public interface StoragePathProvider {
    // 파일 종류별 저장 경로 생성 (예: profile-imgs/uuid.jpg)
    String getProfilePath(String uuid, String fileExtension);
    String getPosterPath(Long concertId, String fileExtension);
    String getSellerDocsPath(String uuid, String fileExtension);

    // 파일 종류별 버킷(또는 최상위 컨테이너) 이름 반환
    String getProfileBucketName();
    String getPosterBucketName();
    String getDocsBucketName();

    // Public URL에서 실제 파일 경로(객체 키) 추출
    Optional<String> extractPathFromPublicUrl(String publicUrl, String bucketName);
}