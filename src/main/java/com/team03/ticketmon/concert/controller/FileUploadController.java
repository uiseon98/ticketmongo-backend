package com.team03.ticketmon.concert.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.team03.ticketmon._global.config.supabase.SupabaseProperties;
import com.team03.ticketmon._global.exception.StorageUploadException;
import com.team03.ticketmon._global.exception.SuccessResponse;
import com.team03.ticketmon._global.util.FileValidator;
import com.team03.ticketmon._global.util.UploadPathUtil;
import com.team03.ticketmon._global.util.uploader.StorageUploader;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class FileUploadController {

	private final StorageUploader storageUploader;
	private final SupabaseProperties supabaseProperties;

	@PostMapping("/poster")
	public ResponseEntity<SuccessResponse<String>> uploadPoster(
		@RequestParam("file") MultipartFile file,
		@RequestParam(required = false) Long concertId
	) {
		try {
			// 파일 검증
			FileValidator.validate(file);

			// 업로드 경로 생성
			String path = concertId != null ?
				UploadPathUtil.getPosterPath(concertId, getFileExtension(file)) :
				"poster/temp/" + UUID.randomUUID();

			// Supabase에 업로드
			String url = storageUploader.uploadFile(
				file,
				supabaseProperties.getPosterBucket(),
				path
			);

			return ResponseEntity.ok(SuccessResponse.of("파일 업로드 성공", url));

		} catch (Exception e) {
			throw new StorageUploadException("포스터 업로드 실패", e);
		}
	}

	private String getFileExtension(MultipartFile file) {
		String filename = file.getOriginalFilename();
		return filename.substring(filename.lastIndexOf(".") + 1);
	}
}