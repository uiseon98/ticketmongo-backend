package com.team03.ticketmon.admin.dto; // admin 패키지 아래 dto 폴더에 생성

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 관리자용 판매자 신청 승인/반려 요청 DTO
 * API-04-02: PATCH /admin/seller-requests/{userID} 에 대한 요청
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminApprovalRequestDTO {

    // 승인/반려할 대상 User의 ID는 URL PathVariable로 받으므로 DTO에 포함하지 않습니다.

    @NotNull(message = "승인 여부는 필수입니다.")
    private Boolean approve; // true: 승인, false: 반려

    @NotBlank(message = "반려 사유는 필수입니다.")
    private String reason; // 반려(approve=false) 시에만 유효한 사유

    // 'approve'가 false일 때만 'reason'이 필수임을 검증하는 로직을 추가할 수 있습니다.
    // (Controller에서 @Valid를 사용하고, 해당 DTO에 @AssertTrue 메서드 추가)
    /*
    @AssertTrue(message = "반려 시 사유는 필수입니다.")
    public boolean isValidReason() {
        if (approve != null && !approve) { // 반려(approve가 false)일 경우
            return reason != null && !reason.trim().isEmpty();
        }
        return true; // 승인(approve가 true)일 경우에는 사유가 필수가 아님
    }
    */
}