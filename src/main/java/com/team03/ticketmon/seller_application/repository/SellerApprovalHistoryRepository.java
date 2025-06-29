package com.team03.ticketmon.seller_application.repository;

import com.team03.ticketmon.seller_application.domain.SellerApprovalHistory;
import com.team03.ticketmon.user.domain.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// SellerApprovalHistory 엔티티와 해당 엔티티의 PK 타입(Long)을 명시
@Repository
public interface SellerApprovalHistoryRepository extends JpaRepository<SellerApprovalHistory, Long> {
    // 추가적인 쿼리 메서드가 필요하다면 여기에 정의할 수 있습니다.
    // 예: List<SellerApprovalHistory> findByUserOrderByCreatedAtDesc(UserEntity user);
    // 예: List<SellerApprovalHistory> findBySellerApplicationOrderByCreatedAtDesc(SellerApplication sellerApplication);
    Optional<SellerApprovalHistory> findTopByUserAndTypeInOrderByCreatedAtDesc(
            UserEntity user,
            List<SellerApprovalHistory.ActionType> types);
}