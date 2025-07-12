package com.team03.ticketmon._global.util.supabase;

import com.team03.ticketmon._global.config.supabase.SupabaseProperties;
import com.team03.ticketmon._global.util.StoragePathProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@Profile("supabase")
@RequiredArgsConstructor
public class SupabasePathProvider implements StoragePathProvider {

    private final SupabaseProperties supabaseProperties; // Supabase 버킷 이름을 가져오기 위해 주입

    public static final String SUPABASE_PUBLIC_URL_PREFIX = "/storage/v1/object/public/";

    @Override
    public String getProfilePath(String uuid, String fileExtension) {
        return String.format("user/profile/%s.%s", uuid, fileExtension);
    }

    @Override
    public String getPosterPath(Long concertId, String fileExtension) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return String.format("concert/poster/%d_%s_%s.%s", concertId, timestamp, uuid, fileExtension);
    }

    @Override
    public String getSellerDocsPath(String uuid, String fileExtension) {
        return String.format("seller/docs/%s.%s", uuid, fileExtension);
    }

    @Override
    public String getProfileBucketName() {
        return supabaseProperties.getProfileBucket();
    }

    @Override
    public String getPosterBucketName() {
        return supabaseProperties.getPosterBucket();
    }

    @Override
    public String getDocsBucketName() {
        return supabaseProperties.getDocsBucket();
    }

    @Override
    public Optional<String> extractPathFromPublicUrl(String publicUrl, String bucketName) {
        if (publicUrl == null || publicUrl.isEmpty() || bucketName == null || bucketName.isEmpty())
            return Optional.empty();
        try {
            final String marker = SUPABASE_PUBLIC_URL_PREFIX + bucketName + "/";
            URI uri = URI.create(publicUrl);
            String path = uri.getPath();
            int idx = path.indexOf(marker);
            return (idx != -1) ? Optional.of(path.substring(idx + marker.length())) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * CloudFront/S3 URL 또는 Supabase URL을 Supabase 환경에 맞는 URL로 변환합니다.
     * 개발 환경에서는 모든 URL이 Supabase URL로 변환되어야 합니다.
     *
     * @param originalUrl 변환할 원본 URL (CloudFront URL, S3 URL, 또는 이미 Supabase URL)
     * @return Supabase URL로 변환된 URL 또는 기본 이미지 URL
     */
    @Override
    public String getCloudFrontImageUrl(String originalUrl) {
        if (originalUrl == null || originalUrl.trim().isEmpty()) {
            // null이거나 빈 URL인 경우 Supabase 기본 이미지 반환
            String baseUrl = supabaseProperties.getUrl();
            if (!baseUrl.endsWith("/")) {
                baseUrl += "/";
            }
            return baseUrl + "storage/v1/object/public/" + supabaseProperties.getPosterBucket() + "/images/basic-poster-image.png";
        }

        // CloudFront/S3 URL → Supabase URL 변환 (운영→개발 전환 대응)
        if (originalUrl.contains("cloudfront.net") || originalUrl.contains("s3.amazonaws.com")) {
            log.debug("🔄 CloudFront/S3 URL을 Supabase URL로 변환: {}", originalUrl);
            return convertCloudFrontUrlToSupabase(originalUrl);
        }

        // 이미 Supabase URL이면 그대로 반환
        return originalUrl;
    }

    /**
     * CloudFront URL 또는 S3 URL을 Supabase URL로 변환합니다.
     * 운영 환경에서 개발 환경으로 전환할 때 사용됩니다.
     *
     * @param cloudFrontUrl 변환할 CloudFront 또는 S3 URL
     * @return Supabase URL로 변환된 URL
     */
    private String convertCloudFrontUrlToSupabase(String cloudFrontUrl) {
        try {
            // CloudFront URL에서 파일 경로 추출
            String path = extractPathFromCloudFrontUrl(cloudFrontUrl);

            if (path == null || path.isEmpty()) {
                log.warn("⚠️ CloudFront URL에서 경로 추출 실패: {}", cloudFrontUrl);
                return cloudFrontUrl;
            }

            // S3 경로 → Supabase 경로 매핑
            String supabasePath = mapS3PathToSupabasePath(path);

            // Supabase URL 생성
            String supabaseUrl = buildSupabaseUrl(supabasePath);

            log.debug("✅ Supabase URL 변환 완료: {} → {}", cloudFrontUrl, supabaseUrl);
            return supabaseUrl;

        } catch (Exception e) {
            log.error("❌ CloudFront URL 변환 실패: {}", cloudFrontUrl, e);
            return cloudFrontUrl; // 변환 실패 시 원본 반환
        }
    }

    /**
     * CloudFront URL 또는 S3 URL에서 파일 경로를 추출합니다.
     *
     * @param url CloudFront 또는 S3 URL
     * @return 추출된 파일 경로 (예: "poster-imgs/filename.jpg")
     */
    private String extractPathFromCloudFrontUrl(String url) {
        try {
            if (url.contains("cloudfront.net/")) {
                // CloudFront URL: https://d123456789.cloudfront.net/poster-imgs/file.jpg
                return url.substring(url.indexOf("cloudfront.net/") + "cloudfront.net/".length());
            } else if (url.contains("s3.amazonaws.com/")) {
                // S3 URL: https://bucket.s3.region.amazonaws.com/poster-imgs/file.jpg
                String[] parts = url.split("/");
                StringBuilder keyBuilder = new StringBuilder();
                boolean foundAwsHost = false;

                for (String part : parts) {
                    if (foundAwsHost && !part.isEmpty()) {
                        if (keyBuilder.length() > 0) keyBuilder.append("/");
                        keyBuilder.append(part);
                    } else if (part.contains("amazonaws.com")) {
                        foundAwsHost = true;
                    }
                }
                return keyBuilder.toString();
            }
            return null;
        } catch (Exception e) {
            log.warn("URL에서 경로 추출 실패: {}", url, e);
            return null;
        }
    }

    /**
     * S3 경로를 Supabase 경로로 매핑합니다.
     * S3의 폴더 구조를 Supabase의 버킷 및 폴더 구조로 변환합니다.
     *
     * @param s3Path S3 경로 (예: "poster-imgs/filename.jpg")
     * @return Supabase 경로 (예: "ticketmon-dev-poster-imgs/concert/poster/filename.jpg")
     */
    private String mapS3PathToSupabasePath(String s3Path) {
        if (s3Path.startsWith("poster-imgs/")) {
            // poster-imgs/filename.jpg → ticketmon-dev-poster-imgs/concert/poster/filename.jpg
            String fileName = s3Path.substring("poster-imgs/".length());
            return "ticketmon-dev-poster-imgs/concert/poster/" + fileName;
        } else if (s3Path.startsWith("profile-imgs/")) {
            // profile-imgs/filename.jpg → ticketmon-dev-profile-imgs/user/profile/filename.jpg
            String fileName = s3Path.substring("profile-imgs/".length());
            return "ticketmon-dev-profile-imgs/user/profile/" + fileName;
        } else if (s3Path.startsWith("seller-docs/")) {
            // seller-docs/filename.pdf → ticketmon-dev-seller-docs/seller/docs/filename.pdf
            String fileName = s3Path.substring("seller-docs/".length());
            return "ticketmon-dev-seller-docs/seller/docs/" + fileName;
        } else {
            // 기본적으로 poster 버킷으로 매핑 (알 수 없는 경로의 경우)
            log.warn("⚠️ 알 수 없는 S3 경로 패턴: {}, poster 버킷으로 매핑", s3Path);
            return "ticketmon-dev-poster-imgs/concert/poster/" + s3Path;
        }
    }

    /**
     * Supabase 경로로부터 완전한 Supabase URL을 생성합니다.
     *
     * @param supabasePath Supabase 내부 경로 (bucket/folder/filename 형태)
     * @return 완전한 Supabase 공개 URL
     */
    private String buildSupabaseUrl(String supabasePath) {
        String baseUrl = supabaseProperties.getUrl();
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        return baseUrl + "storage/v1/object/public/" + supabasePath;
    }
}