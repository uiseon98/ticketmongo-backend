package com.team03.ticketmon.seller_application.repository;

import com.team03.ticketmon.seller_application.domain.SellerApprovalHistory;
import com.team03.ticketmon.user.domain.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// SellerApprovalHistory 엔티티와 해당 엔티티의 PK 타입(Long)을 명시
@Repository
public interface SellerApprovalHistoryRepository extends JpaRepository<SellerApprovalHistory, Long> {

    // 특정 UserEntity에 대한 모든 SellerApprovalHistory 기록을 최신순으로 조회
    // 관리자 페이지의 '판매자 권한 상세 보기 (특정 유저 이력 조회)' 기능 (API-04-04)에 사용
    // OrderByCreatedAtDesc를 통해 생성일시 기준 내림차순으로 정렬
    List<SellerApprovalHistory> findByUserOrderByCreatedAtDesc(UserEntity user);

    // 특정 UserEntity와 특정 ActionType 목록에 해당하는 가장 최신 SellerApprovalHistory를 조회
    // 주로 UserEntity의 lastReason 필드를 채우기 위해 (반려/회수 사유) 사용
    Optional<SellerApprovalHistory> findTopByUserAndTypeInOrderByCreatedAtDesc(
            UserEntity user,
            List<SellerApprovalHistory.ActionType> types);

    // 특정 ActionType으로 필터링된 모든 SellerApprovalHistory 기록을 최신순으로 페이징 조회
    // 관리자 페이지의 '전체 판매자 이력 목록 조회' 기능 (API-04-06)에 사용
    Page<SellerApprovalHistory> findByTypeOrderByCreatedAtDesc(SellerApprovalHistory.ActionType actionType, Pageable pageable);
}