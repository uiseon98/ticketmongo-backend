package com.team03.ticketmon.admin.controller;

import com.team03.ticketmon._global.exception.SuccessResponse;
import com.team03.ticketmon.admin.dto.AdminSellerApplicationListResponseDTO;
import com.team03.ticketmon.admin.service.AdminSellerService;
import com.team03.ticketmon.auth.jwt.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자용 판매자 관리 API 컨트롤러
 * 모든 API는 ADMIN 권한이 필요
 */
@Tag(name = "관리자 판매자 관리", description = "관리자 전용 판매자 신청 및 권한 관리 API")
@RestController
@RequestMapping("/api/admin/seller-requests") // API-04-01 및 API-04-02 경로의 공통 부분
@RequiredArgsConstructor
public class AdminSellerController {

    private final AdminSellerService adminSellerService;

    /**
     * API-04-01: 대기 중인 판매자 신청 목록 조회
     * @param adminUser 현재 로그인된 관리자 정보 (권한 검증용)
     * @return 대기 중인 판매자 신청 목록 DTO 리스트
     */
    @Operation(
            summary = "대기 중인 판매자 신청 목록 조회",
            description = "관리자가 승인 대기 중인 판매자 신청 목록을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음 (ADMIN만 접근 가능)")
    })
    @GetMapping // GET /api/admin/seller-requests
    @PreAuthorize("hasRole('ADMIN')") // ADMIN 역할만 접근 허용
    public ResponseEntity<SuccessResponse<List<AdminSellerApplicationListResponseDTO>>> getPendingApplications(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails adminUser) {

        // 현재 컨트롤러에서 @PreAuthorize를 통해 ADMIN 역할 검증
        // TODO: (선택) 서비스 계층에서 adminUser.getUserId()를 사용하여 관리자 권한을 최종적으로 검증하거나 로깅할 수 있습니다.
        // 현재 AdminSellerService.getPendingApplications()는 adminId 파라미터를 받지 않으므로,
        // 이를` 위해 서비스 메서드 시그니처 변경 및 추가 로직 구현이 필요할 수 있습니다.
        List<AdminSellerApplicationListResponseDTO> pendingApplications = adminSellerService.getPendingSellerApplications();

        return ResponseEntity.ok(SuccessResponse.of("대기 중인 판매자 신청 목록 조회 성공", pendingApplications));
    }
}