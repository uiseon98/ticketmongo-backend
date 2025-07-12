package com.team03.ticketmon._global.util.supabase;

import com.team03.ticketmon._global.config.supabase.SupabaseProperties;
import com.team03.ticketmon._global.util.StoragePathProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;

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

    @Override
    public String getCloudFrontImageUrl(String publicUrlFromSupabase) {
        // Supabase 환경에서는 publicUrlFromSupabase 자체가 이미 최종적으로 접근 가능한 URL입니다.
        // 따라서 이 URL을 그대로 반환하면 됩니다.
        // 만약 null이거나 비어있는 경우, AppProperties의 baseUrl을 활용한 기본 이미지 경로를 반환합니다.

        if (publicUrlFromSupabase == null || publicUrlFromSupabase.trim().isEmpty()) {
            // 여러분의 AppProperties (baseUrl)에 Supabase의 기본 도메인(예: https://your-supabase-url.supabase.co)이
            // 설정되어 있다면 그것을 사용하여 기본 이미지를 구성할 수 있습니다.
            // 여기서는 임시로 Supabase Properties를 사용합니다.
            // (주의: AppProperties는 S3PathProvider에서만 주입되므로, 여기서는 직접 사용하지 않습니다.
            //  대신 SupabaseProperties의 url을 사용하여 구성하거나, 더미 URL을 반환합니다.)
            String baseUrl = supabaseProperties.getUrl();
            if (!baseUrl.endsWith("/")) {
                baseUrl += "/";
            }
            return baseUrl + "storage/v1/object/public/" + supabaseProperties.getPosterBucket() + "/images/basic-poster-image.png"; // Supabase의 기본 이미지 경로
        }
        return publicUrlFromSupabase; // Supabase의 공개 URL을 그대로 반환
    }
}