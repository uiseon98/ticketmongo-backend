package com.team03.ticketmon._global.util.uploader.s3;

import com.team03.ticketmon._global.exception.StorageUploadException;
import com.team03.ticketmon._global.util.uploader.StorageUploader;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.net.URL; // URL 클래스 임포트
import java.time.Duration; // Duration 클래스 임포트

/**
 * AWS S3 업로더 구현체
 *
 * <p>
 * {@link StorageUploader} 인터페이스의 AWS S3 구현체입니다.
 * </p>
 */
@Component
@Profile("s3")
@RequiredArgsConstructor
public class S3Uploader implements StorageUploader {

    private final S3Client s3Client; // S3Client 주입

    // application-prod.yml에서 설정한 S3 버킷 이름
    @org.springframework.beans.factory.annotation.Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    @Override
    public String uploadFile(MultipartFile file, String bucket, String path) { // bucket은 S3Uploader에서는 사용하지 않지만 인터페이스 일관성을 위해 유지
        if (file.isEmpty()) {
            throw new StorageUploadException("업로드할 파일이 비어 있습니다.");
        }

        // S3 Key (객체 경로 및 파일명) 생성
        // path는 이미 "profile-imgs/UUID.jpg"와 같은 형태로 넘어오므로 그대로 사용
        String s3Key = path;

        try {
            // PutObjectRequest 생성
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName) // 실제 업로드될 버킷 이름
                    .key(s3Key) // 객체 키 (경로 포함 파일명)
                    .contentType(file.getContentType()) // 파일 타입 (예: image/jpeg)
                    .contentLength(file.getSize()) // 파일 크기
                    .build();

            // RequestBody를 사용하여 파일 데이터 전송
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            // 업로드된 파일의 Public URL 구성
            // S3 퍼블릭 URL 형식: https://[버킷이름].s3.[리전].amazonaws.com/[객체키]
            // 현재 퍼블릭 액세스가 차단되어 있으므로, 이 URL은 직접 접근 불가 (ALB 등을 통해 서비스해야 함)
            String publicUrl = String.format("https://%s.s3.%s.amazonaws.com/%s",
                    bucketName,
                    s3Client.serviceClientConfiguration().region().id(), // S3Client에서 리전 정보 가져오기
                    s3Key);

            return publicUrl;

        } catch (IOException e) {
            throw new StorageUploadException("파일 스트림 처리 중 오류 발생", e);
        } catch (S3Exception e) {
            throw new StorageUploadException("S3 업로드 중 오류 발생: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new StorageUploadException("파일 업로드 중 알 수 없는 오류 발생", e);
        }
    }

    @Override
    public void deleteFile(String bucket, String fullPath) { // bucket은 S3Uploader에서는 사용하지 않지만 인터페이스 일관성을 위해 유지
        if (fullPath == null || fullPath.isEmpty()) {
            return;
        }

        // S3 Key (객체 경로 및 파일명) 추출
        // fullPath가 S3 URL 형태일 수 있으므로, 실제 S3 Key만 추출하는 로직 필요
        // 예: https://bucket.s3.region.amazonaws.com/profile-imgs/UUID.jpg -> profile-imgs/UUID.jpg
        String s3KeyToDelete = extractS3KeyFromUrl(fullPath, bucketName, s3Client.serviceClientConfiguration().region().id());
        if (s3KeyToDelete == null || s3KeyToDelete.isEmpty()) {
            // 올바른 S3 Key를 추출하지 못하면 삭제 시도 안함
            return;
        }

        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName) // 실제 버킷 이름
                    .key(s3KeyToDelete) // 삭제할 객체 키
                    .build();

            s3Client.deleteObject(deleteObjectRequest);

        } catch (S3Exception e) {
            throw new StorageUploadException("S3 파일 삭제 중 오류 발생: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new StorageUploadException("파일 삭제 중 알 수 없는 오류 발생", e);
        }
    }

    // Public URL에서 S3 Key(객체 경로)를 추출하는 헬퍼 메서드
    private String extractS3KeyFromUrl(String publicUrl, String bucketName, String region) {
        // S3 Public URL 형식: https://[bucket].s3.[region].amazonaws.com/[key]
        // 또는 가상 호스팅 방식: https://[bucket].s3.amazonaws.com/[key] (리전은 URL에 없을 수 있음)
        String expectedPrefix = String.format("https://%s.s3.%s.amazonaws.com/", bucketName, region);
        String virtualHostedPrefix = String.format("https://%s.s3.amazonaws.com/", bucketName);

        if (publicUrl.startsWith(expectedPrefix)) {
            return publicUrl.substring(expectedPrefix.length());
        } else if (publicUrl.startsWith(virtualHostedPrefix)) {
            return publicUrl.substring(virtualHostedPrefix.length());
        } else {
            // 다른 형식의 URL이거나, 버킷/리전 정보가 일치하지 않는 경우
            return null;
        }
    }
}