package com.team03.ticketmon._global.util.uploader.s3;

import com.team03.ticketmon._global.util.uploader.StorageUploader;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * AWS S3 업로더 구현체 (마이그레이션 대비 스켈레톤)
 *
 * <p>
 * 현재는 SupabaseStorageUploader만 빈으로 등록되어 있습니다.<br>
 * <b>Supabase → S3 마이그레이션 시 @Component를 활성화하여 사용하세요.</b>
 * </p>
 *
 * <p>
 * (참고) 빈 충돌 방지를 위해 지금은 @Component를 주석 처리했습니다.<br>
 * 실제 S3 연동 시 주석을 해제하고, 필요시 @Profile("s3") 등으로 구분해 사용할 수 있습니다.
 * </p>
 */
// @Component   // S3 마이그레이션 시 주석 해제
// @Profile("s3")
public class S3Uploader implements StorageUploader {
    @Override
    public String uploadFile(MultipartFile file, String bucket, String path) {
        // TODO: S3 마이그레이션 시 구현
        throw new UnsupportedOperationException("아직 S3 업로드는 구현되지 않았습니다.");
    }
}

