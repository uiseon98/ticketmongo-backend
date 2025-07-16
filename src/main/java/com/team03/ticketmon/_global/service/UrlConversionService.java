package com.team03.ticketmon._global.service;

import com.team03.ticketmon._global.util.StoragePathProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * [URL 변환 서비스]
 * <br>
 * 이 서비스는 S3 직접 URL을 CloudFront URL로 변환하는 공통 로직을 제공합니다.<br>
 * 모든 서비스에서 이미지나 미디어 파일 등의 URL 변환이 필요할 때 이 서비스를 사용하여 일관성을 유지합니다.
 *
 * <p>CloudFront를 통해 콘텐츠를 제공함으로써 다음과 같은 이점을 얻을 수 있습니다:</p>
 * <ul>
 * <li>콘텐츠 전송 속도 향상 (CDN 활용)</li>
 * <li>보안 강화 (S3 직접 접근 제한)</li>
 * <li>비용 효율성 증대 (캐싱 활용)</li>
 * </ul>
 *
 * <p>주요 기능:</p>
 * <ul>
 * <li>개별 S3 URL을 CloudFront URL로 변환</li>
 * <li>여러 S3 URL 목록을 일괄적으로 CloudFront URL 목록으로 변환</li>
 * <li>DTO 객체의 URL 필드 변환을 위한 헬퍼 메서드 제공</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UrlConversionService {

    private final StoragePathProvider storagePathProvider;

    /**
     * **단일 S3 직접 URL을 CloudFront URL로 변환합니다.**
     *
     * <p>입력된 URL이 null이거나 비어있으면 원본 URL을 그대로 반환합니다.</p>
     * <p>{@link StoragePathProvider}를 사용하여 실제 변환 로직을 수행합니다.</p>
     * <p>URL 변환이 성공적으로 이루어졌을 경우, 디버그 레벨로 변환 전/후 URL을 로깅합니다.</p>
     *
     * @param url 변환할 URL. S3 직접 URL (예: `https://your-s3-bucket.s3.ap-northeast-2.amazonaws.com/path/to/image.jpg`)
     * 또는 이미 CloudFront URL일 수 있습니다.
     * @return CloudFront URL (예: `https://your-cloudfront-distribution.cloudfront.net/path/to/image.jpg`).
     * 만약 입력된 URL이 이미 CloudFront URL이거나 변환이 필요 없는 경우 원본 URL이 반환됩니다.
     */
    public String convertToCloudFrontUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }

        String convertedUrl = storagePathProvider.getCloudFrontImageUrl(url);

        // 로그는 실제로 변환이 일어난 경우에만 출력
        if (!url.equals(convertedUrl)) {
            log.debug("URL 변환 완료: {} -> {}", url, convertedUrl);
        }

        return convertedUrl;
    }

    /**
     * 여러 URL을 일괄 변환
     *
     * @param urls 변환할 URL 목록
     * @return CloudFront URL 목록
     */
    public List<String> convertToCloudFrontUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return urls;
        }

        return urls.stream()
                .map(this::convertToCloudFrontUrl)
                .collect(Collectors.toList());
    }

    /**
     * DTO 객체의 URL 필드들을 일괄 변환하는 헬퍼 메서드
     * 주로 응답 DTO 생성 시 사용
     *
     * @param url 변환할 URL
     * @return 변환된 URL
     */
    public String ensureCloudFrontUrl(String url) {
        return convertToCloudFrontUrl(url);
    }
}