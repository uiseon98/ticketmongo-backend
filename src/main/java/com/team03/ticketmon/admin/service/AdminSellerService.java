package com.team03.ticketmon.admin.service;

import com.team03.ticketmon.admin.dto.AdminSellerApplicationListResponseDTO;
import com.team03.ticketmon.seller_application.domain.SellerApplication;
import com.team03.ticketmon.seller_application.repository.SellerApplicationRepository;
import com.team03.ticketmon.seller_application.domain.SellerApplication.SellerApplicationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 관리자용 판매자 관리 비즈니스 로직 서비스
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 조회 기능이 많으므로 기본적으로 읽기 전용 트랜잭션 설정
public class AdminSellerService {

    private final SellerApplicationRepository sellerApplicationRepository;

    /**
     * API-04-01: 대기 중인 판매자 신청 목록 조회
     * @return 대기 중인 판매자 신청 목록 DTO 리스트
     */
    public List<AdminSellerApplicationListResponseDTO> getPendingSellerApplications() {
        // SellerApplicationStatus.SUBMITTED 상태의 신청 목록을 조회
        List<SellerApplication> pendingApplications = sellerApplicationRepository.findByStatus(SellerApplicationStatus.SUBMITTED);

        // 엔티티 리스트를 DTO 리스트로 변환하여 반환
        return pendingApplications.stream()
                .map(AdminSellerApplicationListResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }
}