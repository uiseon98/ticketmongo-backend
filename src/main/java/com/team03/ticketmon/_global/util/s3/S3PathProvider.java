package com.team03.ticketmon._global.util.s3;

import com.team03.ticketmon._global.util.StoragePathProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;

@Component
@Profile("s3")
public class S3PathProvider implements StoragePathProvider {

    @Value("${cloud.aws.s3.bucket}")
    private String s3BucketName;
    @Value("${cloud.aws.s3.profile-prefix}")
    private String profilePrefix;
    @Value("${cloud.aws.s3.poster-prefix}")
    private String posterPrefix;
    @Value("${cloud.aws.s3.seller-docs-prefix}")
    private String sellerDocsPrefix;

    @Override
    public String getProfilePath(String uuid, String fileExtension) {
        return String.format("%s%s.%s", profilePrefix, uuid, fileExtension);
    }

    @Override
    public String getPosterPath(Long concertId, String fileExtension) {
        return String.format("%s%d.%s", posterPrefix, concertId, fileExtension);
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
                // 이 경우 URI.getPath()는 이미 객체 키만 반환합니다 (앞에 슬래시 있을 수 있음)
                return Optional.of(path.startsWith("/") ? path.substring(1) : path);
            }
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}