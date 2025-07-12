package com.team03.ticketmon._global.util.s3;

import com.team03.ticketmon._global.config.AppProperties;
import com.team03.ticketmon._global.util.StoragePathProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

@Component
@Profile("s3")
@RequiredArgsConstructor
public class S3PathProvider implements StoragePathProvider {

    @Value("${cloud.aws.s3.bucket}")
    private String s3BucketName;
    @Value("${cloud.aws.s3.profile-prefix}")
    private String profilePrefix;
    @Value("${cloud.aws.s3.poster-prefix}")
    private String posterPrefix;
    @Value("${cloud.aws.s3.seller-docs-prefix}")
    private String sellerDocsPrefix;
    @Value("${cloud.aws.region.static}")
    private String region;

    private final AppProperties appProperties;

    @Override
    public String getProfilePath(String uuid, String fileExtension) {
        return String.format("%s%s.%s", profilePrefix, uuid, fileExtension);
    }

    @Override
    public String getPosterPath(Long concertId, String fileExtension) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return String.format("%s%d_%s_%s.%s", posterPrefix, concertId, timestamp, uuid, fileExtension);
    }

    @Override
    public String getSellerDocsPath(String uuid, String fileExtension) {
        return String.format("%s%s.%s", sellerDocsPrefix, uuid, fileExtension);
    }

    @Override
    public String getProfileBucketName() {
        return s3BucketName;
    }

    @Override
    public String getPosterBucketName() {
        return s3BucketName;
    }

    @Override
    public String getDocsBucketName() {
        return s3BucketName;
    }

    // S3 직접 URL 패턴 확인 공통 메서드
    public boolean isS3DirectUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        String s3UrlPattern = getS3DirectUrlPrefix();
        return url.startsWith(s3UrlPattern);
    }

    // S3 직접 URL 접두사 생성 공통 메서드
    private String getS3DirectUrlPrefix() {
        return String.format("https://%s.s3.%s.amazonaws.com/", s3BucketName, region);
    }

    @Override
    public Optional<String> extractPathFromPublicUrl(String publicUrl, String bucketName) {
        if (publicUrl == null || publicUrl.isEmpty() || bucketName == null || bucketName.isEmpty())
            return Optional.empty();

        try {
            URI uri = URI.create(publicUrl);
            String path = uri.getPath();

            // Path-style URL (예: /bucket-name/key)
            if (path.startsWith("/" + bucketName + "/")) {
                return Optional.of(path.substring(("/" + bucketName + "/").length()));
            } else if (path.startsWith("/" + bucketName)) {
                // 루트 경로에 바로 파일이 있는 경우 (예: /bucket-name -> file.jpg)
                return Optional.of(path.substring(("/" + bucketName).length()).replaceFirst("/", ""));
            } else {
                // Virtual-hosted style URL (예: bucket-name.s3.region.amazonaws.com/key)
                // URI.getPath()는 이미 객체 키만 반환 (앞에 슬래시 있을 수 있음)
                return Optional.of(path.startsWith("/") ? path.substring(1) : path);
            }
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public String getCloudFrontImageUrl(String s3DirectUrl) {
        // 공통 메서드 사용하여 S3 URL 패턴 확인
        if (!isS3DirectUrl(s3DirectUrl)) {
            // 이미 CloudFront URL이거나 다른 URL인 경우
            if (s3DirectUrl == null || s3DirectUrl.trim().isEmpty()) {
                // null이거나 빈 URL인 경우 기본 이미지 반환
                return appProperties.baseUrl() + "/images/basic-poster-image.png";
            }
            // 이미 CloudFront URL이거나 다른 형태의 URL인 경우 그대로 반환
            return s3DirectUrl;
        }

        // S3 직접 URL에서 객체 키(key)만 추출
        String s3DirectUrlPrefix = getS3DirectUrlPrefix();
        String s3Key = s3DirectUrl.substring(s3DirectUrlPrefix.length());

        // AppProperties에서 가져온 baseUrl과 객체 키를 조합하여 CloudFront URL 생성
        return appProperties.baseUrl() + "/" + s3Key;
    }
}