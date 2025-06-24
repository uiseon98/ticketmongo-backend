package com.team03.ticketmon.seller_application.dto;

import com.team03.ticketmon.user.domain.entity.UserEntity; // UserEntity의 Role, ApprovalStatus 사용
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 판매자 권한 상태 응답 DTO
 * <p>
 * {@code GET /users/me/seller-status} 요청 시 사용자에게 현재 판매자 권한 상태를 응답합니다.
 * </p>
 * <p>
 * 이 DTO는 사용자의 역할, 판매자 승인 상태, 마지막 처리 사유,
 * 그리고 판매자 권한 재신청 및 철회 가능 여부를 프론트엔드에 제공합니다.
 * 추가적으로 신청일 및 마지막 처리일 정보를 포함합니다.
 * </p>
 *
 * @version 1.0
 * @see com.team03.ticketmon.seller_application.service.SellerApplicationService#getSellerApplicationStatus(Long)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerApplicationStatusResponseDTO {

    private UserEntity.Role role;                  // 사용자의 현재 역할 (USER, SELLER, ADMIN)
    private UserEntity.ApprovalStatus approvalStatus; // 사용자의 판매자 승인 상태
    private String lastReason;                 // 가장 최근 반려 또는 회수된 사유 (이력 테이블에서 조합)
    private Boolean canReapply;                // 판매자 권한 재신청 가능 여부
    private Boolean canWithdraw;               // 현재 판매자 권한 철회 가능 여부

    // 추가 정보: 신청일 및 마지막 처리일
    private LocalDateTime applicationDate;     // 판매자 권한 신청일 (SellerApplication.createdAt)
    private LocalDateTime lastProcessedDate;   // 판매자 권한 마지막 처리일 (SellerApplication.updatedAt)
}