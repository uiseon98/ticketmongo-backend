package com.team03.ticketmon._global.util;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import java.util.Map;

public class FileUtil {
    private static final Map<String, String> MIME_TO_EXT = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "application/pdf", "pdf"
    );

    public static String getExtensionFromMimeType(String mimeType) {
        if (mimeType == null || !MIME_TO_EXT.containsKey(mimeType)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE, "지원하지 않는 MIME 타입입니다: " + mimeType);
        }
        return MIME_TO_EXT.get(mimeType);
    }
}