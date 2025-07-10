package com.team03.ticketmon.concert.controller;

import java.net.URL;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.team03.ticketmon._global.config.supabase.SupabaseProperties;
import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon._global.exception.StorageUploadException;
import com.team03.ticketmon._global.exception.SuccessResponse;
import com.team03.ticketmon._global.util.FileValidator;
import com.team03.ticketmon._global.util.UploadPathUtil;
import com.team03.ticketmon._global.util.uploader.supabase.SupabaseUploader;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.concert.repository.SellerConcertRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Slf4j
public class FileUploadController {

	private final SupabaseProperties supabaseProperties;
	private final SupabaseUploader supabaseUploader;
	private final SellerConcertRepository sellerConcertRepository;
	private final ConcertRepository concertRepository;

	@PostMapping("/poster")
	@Transactional
	public ResponseEntity<SuccessResponse<String>> uploadPoster(
		@RequestParam("file") MultipartFile file,
		@RequestParam(required = false) Long concertId
	) {
		String uploadedUrl = null;
		try {
			// 파일 검증
			FileValidator.validate(file);

			// 업로드 경로 생성
			String path = concertId != null ?
				UploadPathUtil.getPosterPath(concertId, getFileExtension(file)) :
				"poster/temp/" + UUID.randomUUID();

			// Supabase에 업로드
			uploadedUrl = supabaseUploader.uploadFile(
				file,
				supabaseProperties.getPosterBucket(),
				path
			);
			log.info("✅ 파일 업로드 성공 - URL: {}", uploadedUrl);

			// concertId가 있으면 DB에 URL 저장
			if (concertId != null) {
				log.info("🔍 DB에 포스터 URL 저장 시작 - concertId: {}", concertId);

				// 콘서트 존재 여부 확인
				Optional<Concert> concertOpt = concertRepository.findById(concertId);
				if (concertOpt.isEmpty()) {
					log.error("❌ 존재하지 않는 콘서트 - concertId: {}", concertId);
					// 업로드된 파일 삭제 (롤백)
					try {
						supabaseUploader.deleteFile(supabaseProperties.getPosterBucket(), uploadedUrl);
						log.info("🔄 업로드 롤백 완료");
					} catch (Exception rollbackEx) {
						log.error("❌ 업로드 롤백 실패", rollbackEx);
					}
					throw new BusinessException(ErrorCode.CONCERT_NOT_FOUND);
				}

				// DB에 URL 업데이트
				Concert concert = concertOpt.get();
				concert.setPosterImageUrl(uploadedUrl);
				concertRepository.save(concert);

				log.info("✅ DB에 포스터 URL 저장 완료 - concertId: {}, URL: {}", concertId, uploadedUrl);
			}

			return ResponseEntity.ok(SuccessResponse.of("파일 업로드 성공", uploadedUrl));

		} catch (BusinessException e) {
			// 비즈니스 예외 시 업로드된 파일 롤백
			if (uploadedUrl != null) {
				rollbackUploadedFile(uploadedUrl);
			}
			throw e;
		} catch (Exception e) {
			// 일반 예외 시 업로드된 파일 롤백
			if (uploadedUrl != null) {
				rollbackUploadedFile(uploadedUrl);
			}
			throw new StorageUploadException("포스터 업로드 실패", e);
		}
	}


	/**
	 * 업로드된 파일 롤백 (삭제)
	 */
	private void rollbackUploadedFile(String uploadedUrl) {
		try {
			supabaseUploader.deleteFile(supabaseProperties.getPosterBucket(), uploadedUrl);
			log.info("🔄 업로드 실패로 인한 파일 롤백 완료 - URL: {}", uploadedUrl);
		} catch (Exception rollbackException) {
			log.error("❌ 파일 롤백 실패 (수동 삭제 필요) - URL: {}", uploadedUrl, rollbackException);
		}
	}

	private String getFileExtension(MultipartFile file) {
		String filename = file.getOriginalFilename();
		return filename.substring(filename.lastIndexOf(".") + 1);
	}

	@DeleteMapping("/poster/{concertId}")
	@Transactional
	public ResponseEntity<?> deletePosterByConcert(
		@PathVariable Long concertId,
		@RequestParam Long sellerId
	) {
		try {
			log.info("🗑️ 콘서트 포스터 삭제 요청 - concertId: {}, sellerId: {}", concertId, sellerId);

			// 1. 권한 검증
			log.info("🔍 단계 1: 권한 검증 시작");
			if (!sellerConcertRepository.existsByConcertIdAndSellerId(concertId, sellerId)) {
				log.warn("❌ 권한 검증 실패 - concertId: {}, sellerId: {}", concertId, sellerId);
				return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(Map.of(
						"success", false,
						"message", "해당 콘서트의 포스터를 삭제할 권한이 없습니다."
					));
			}
			log.info("✅ 단계 1: 권한 검증 완료");

			// 2. 콘서트 정보 조회해서 현재 포스터 URL 확인
			log.info("🔍 단계 2: 콘서트 조회 시작");
			Optional<Concert> concertOpt = concertRepository.findById(concertId);
			if (concertOpt.isEmpty()) {
				log.warn("❌ 콘서트를 찾을 수 없음 - concertId: {}", concertId);
				return ResponseEntity.notFound().build();
			}
			log.info("✅ 단계 2: 콘서트 조회 완료");

			Concert concert = concertOpt.get();
			log.info("🔍 단계 3: 포스터 URL 확인 시작");
			String currentPosterUrl = concert.getPosterImageUrl();
			log.info("🔍 현재 포스터 URL: [{}]", currentPosterUrl);

			if (currentPosterUrl == null || currentPosterUrl.trim().isEmpty()) {
				log.info("ℹ️ 삭제할 포스터가 없음 - concertId: {}", concertId);
				return ResponseEntity.ok(Map.of(
					"success", true,
					"message", "삭제할 포스터 이미지가 없습니다.",
					"alreadyEmpty", true
				));
			}
			log.info("✅ 단계 3: 포스터 URL 확인 완료 - URL 존재함");

			// 3. Supabase에서 파일 삭제
			log.info("🔍 단계 4: Supabase 파일 삭제 시작");
			try {
				supabaseUploader.deleteFile(supabaseProperties.getPosterBucket(), currentPosterUrl);
				log.info("✅ Supabase 파일 삭제 완료 - URL: {}", currentPosterUrl);
			} catch (Exception supabaseException) {
				log.warn("⚠️ Supabase 파일 삭제 실패 (계속 진행) - URL: {}", currentPosterUrl, supabaseException);
			}
			log.info("✅ 단계 4: Supabase 파일 삭제 처리 완료");

			// 4. DB에서 poster_image_url 필드 null로 업데이트
			log.info("🔍 단계 5: DB 업데이트 시작");
			int updatedRows = sellerConcertRepository.updatePosterImageUrl(concertId, sellerId, null);
			log.info("🔍 DB 업데이트 결과: {} rows affected", updatedRows);

			if (updatedRows == 0) {
				log.warn("⚠️ DB 업데이트 실패 - 권한 없음 또는 존재하지 않는 콘서트: concertId={}, sellerId={}", concertId, sellerId);
				return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(Map.of(
						"success", false,
						"message", "포스터 삭제 권한이 없거나 존재하지 않는 콘서트입니다."
					));
			}
			log.info("✅ 단계 5: DB 업데이트 완료");

			log.info("✅ 포스터 삭제 완료 - concertId: {}", concertId);

			return ResponseEntity.ok(Map.of(
				"success", true,
				"message", "콘서트 포스터가 삭제되었습니다.",
				"deletedUrl", currentPosterUrl,
				"concertId", concertId
			));

		} catch (BusinessException e) {
			log.error("❌ 비즈니스 로직 오류 - concertId: {}", concertId, e);
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(Map.of(
					"success", false,
					"message", e.getMessage()
				));

		} catch (Exception e) {
			log.error("❌ 콘서트 포스터 삭제 실패 - concertId: {}", concertId, e);

			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of(
					"success", false,
					"message", "포스터 삭제 중 오류가 발생했습니다: " + e.getMessage()
				));
		}
	}
}