package com.team03.ticketmon._global.util.s3;

import com.team03.ticketmon._global.config.AppProperties;
import com.team03.ticketmon._global.util.StoragePathProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@Profile("prod")
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

    /**
     * S3 직접 URL 또는 Supabase URL을 CloudFront URL로 변환합니다.
     *
     * @param originalUrl 변환할 원본 URL (S3 직접 URL, Supabase URL, 또는 이미 CloudFront URL)
     * @return CloudFront URL로 변환된 URL 또는 기본 이미지 URL
     */
    @Override
    public String getCloudFrontImageUrl(String originalUrl) {
        if (originalUrl == null || originalUrl.trim().isEmpty()) {
            // null이거나 빈 URL인 경우 기본 이미지 반환
            String baseUrl = appProperties.baseUrl();
            String imagePath = "images/basic-poster-image.png";
            if (baseUrl.endsWith("/")) {
                return baseUrl + imagePath;
            } else {
                return baseUrl + "/" + imagePath;
            }
        }

        // Supabase URL → CloudFront URL 변환 (마이그레이션된 데이터 대응)
        if (originalUrl.contains("supabase.co")) {
            log.debug("🔄 Supabase URL을 CloudFront URL로 변환: {}", originalUrl);
            return convertSupabaseUrlToCloudFront(originalUrl);
        }

        // S3 직접 URL → CloudFront URL 변환
        if (isS3DirectUrl(originalUrl)) {
            log.debug("🔄 S3 직접 URL을 CloudFront URL로 변환: {}", originalUrl);
            String s3DirectUrlPrefix = getS3DirectUrlPrefix();
            String s3Key = originalUrl.substring(s3DirectUrlPrefix.length());

            String baseUrl = appProperties.baseUrl();
            if (baseUrl.endsWith("/")) {
                return baseUrl + s3Key;
            } else {
                return baseUrl + "/" + s3Key;
            }
        }

        // 이미 CloudFront URL이거나 다른 형태의 URL인 경우 그대로 반환
        return originalUrl;
    }

    /**
     * Supabase URL을 CloudFront URL로 변환합니다.
     * 마이그레이션 과정에서 DB에 저장된 Supabase URL들을 CloudFront URL로 변환하는 용도입니다.
     *
     * @param supabaseUrl 변환할 Supabase URL
     * @return CloudFront URL로 변환된 URL
     */
    private String convertSupabaseUrlToCloudFront(String supabaseUrl) {
        try {
            // Supabase URL 패턴:
            // https://weorddukxzwozckuxqkx.supabase.co/storage/v1/object/public/bucket-name/path/file.ext
            String publicMarker = "/storage/v1/object/public/";
            int markerIndex = supabaseUrl.indexOf(publicMarker);

            if (markerIndex == -1) {
                log.warn("⚠️ Supabase URL 패턴이 올바르지 않음: {}", supabaseUrl);
                return supabaseUrl; // 패턴이 안 맞으면 원본 반환
            }

            // "/storage/v1/object/public/" 이후 부분 추출
            String pathAfterMarker = supabaseUrl.substring(markerIndex + publicMarker.length());

            // bucket-name/path에서 첫 번째 "/" 이후가 실제 파일 경로
            int firstSlash = pathAfterMarker.indexOf('/');
            if (firstSlash == -1) {
                log.warn("⚠️ Supabase URL 경로가 올바르지 않음: {}", supabaseUrl);
                return supabaseUrl;
            }

            String bucketName = pathAfterMarker.substring(0, firstSlash);
            String filePath = pathAfterMarker.substring(firstSlash + 1);

            log.debug("📁 추출된 Supabase 버킷: {}, 파일경로: {}", bucketName, filePath);

            // 버킷별 S3 경로 매핑
            String s3Path = mapSupabaseBucketToS3Path(bucketName, filePath);

            String baseUrl = appProperties.baseUrl();
            String cloudFrontUrl;
            if (baseUrl.endsWith("/")) {
                cloudFrontUrl = baseUrl + s3Path;
            } else {
                cloudFrontUrl = baseUrl + "/" + s3Path;
            }

            log.debug("✅ CloudFront URL 변환 완료: {} → {}", supabaseUrl, cloudFrontUrl);
            return cloudFrontUrl;

        } catch (Exception e) {
            log.error("❌ Supabase URL 변환 실패: {}", supabaseUrl, e);
            return supabaseUrl; // 변환 실패 시 원본 반환
        }
    }

    /**
     * Supabase 버킷명을 S3 경로로 매핑합니다.
     * 마이그레이션 과정에서 Supabase 버킷 구조를 S3 폴더 구조로 변환합니다.
     *
     * @param supabaseBucket Supabase 버킷명
     * @param filePath Supabase 내 파일 경로
     * @return S3에서 사용할 파일 경로
     */
    private String mapSupabaseBucketToS3Path(String supabaseBucket, String filePath) {
        switch (supabaseBucket) {
            case "ticketmon-dev-poster-imgs":
                // concert/poster/1_1234567890_abc123.jpg → poster-imgs/1_1234567890_abc123.jpg
                return "poster-imgs/" + extractFileName(filePath);

            case "ticketmon-dev-profile-imgs":
                // user/profile/uuid123.jpg → profile-imgs/uuid123.jpg
                return "profile-imgs/" + extractFileName(filePath);

            case "ticketmon-dev-seller-docs":
                // seller/docs/doc456.pdf → seller-docs/doc456.pdf
                return "seller-docs/" + extractFileName(filePath);

            default:
                log.warn("⚠️ 알 수 없는 Supabase 버킷: {}", supabaseBucket);
                return extractFileName(filePath); // 알 수 없는 버킷은 파일명만 사용
        }
    }

    /**
     * 파일 경로에서 파일명만 추출합니다.
     * 폴더 구조를 제거하고 실제 파일명만 반환합니다.
     *
     * @param filePath 전체 파일 경로
     * @return 파일명만 추출된 문자열
     */
    private String extractFileName(String filePath) {
        // 예: "concert/poster/1_1234567890_abc123.jpg" → "1_1234567890_abc123.jpg"
        int lastSlash = filePath.lastIndexOf('/');
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }
}