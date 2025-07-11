package com.team03.ticketmon._global.util.supabase;

import com.team03.ticketmon._global.config.supabase.SupabaseProperties;
import com.team03.ticketmon._global.util.StoragePathProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import java.net.URI;
import java.util.Optional;

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
        return String.format("concert/poster/%d.%s", concertId, fileExtension);
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
}