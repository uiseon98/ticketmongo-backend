package com.team03.ticketmon._global.util;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public class FileValidator {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 파일 크기 제한 10MB로 변경
    private static final List<String> ALLOWED_TYPES = List.of("image/jpeg", "image/png", "image/webp", "application/pdf");

    public static void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어 있습니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("파일 크기는 10MB를 초과할 수 없습니다.");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("허용되지 않은 파일 형식입니다: " + file.getContentType());
        }
    }
}
