package com.team03.ticketmon.seller_application.service;

import com.team03.ticketmon._global.util.FileValidator;
import com.team03.ticketmon._global.util.uploader.StorageUploader;
import com.team03.ticketmon._global.util.UploadPathUtil;
import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;

import com.team03.ticketmon.seller_application.domain.SellerApplication;
import com.team03.ticketmon.seller_application.domain.SellerApplication.SellerApplicationStatus;
import com.team03.ticketmon.seller_application.domain.SellerApprovalHistory;

import com.team03.ticketmon.seller_application.dto.ApplicantInformationResponseDTO;
import com.team03.ticketmon.seller_application.dto.SellerApplicationRequestDTO;
import com.team03.ticketmon.seller_application.dto.SellerApplicationStatusResponseDTO;

import com.team03.ticketmon.seller_application.repository.SellerApplicationRepository;
import com.team03.ticketmon.seller_application.repository.SellerApprovalHistoryRepository;

import com.team03.ticketmon.user.domain.entity.UserEntity;
import com.team03.ticketmon.user.domain.entity.UserEntity.ApprovalStatus;
import com.team03.ticketmon.user.domain.entity.UserEntity.Role;

import com.team03.ticketmon.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

// 콘서트 도메인 의존성 추가 (판매자 권한 철회 조건 강화용)
import com.team03.ticketmon.concert.repository.SellerConcertRepository;
import com.team03.ticketmon.concert.domain.enums.ConcertStatus;
import com.team03.ticketmon.concert.domain.Concert;

// SellerApplicationStatus Enum 값들을 static import로 사용 (내부 ENUM 사용 / 선택 사항)
import static com.team03.ticketmon.seller_application.domain.SellerApplication.SellerApplicationStatus.*;

/**
 * 판매자 권한 신청 관련 비즈니스 로직을 처리하는 서비스 클래스입니다.
 *
 * 환경에 따라 Supabase 또는 S3를 유연하게 사용할 수 있도록 리팩토링되었습니다.
 * 기존 코드 구조를 최대한 유지하면서 스토리지 의존성만 추상화했습니다.
 */
@Service
@RequiredArgsConstructor
public class SellerApplicationService {

    private final UserRepository userRepository;
    private final SellerApplicationRepository sellerApplicationRepository;
    private final StorageUploader storageUploader; // 환경별 구현체가 자동 주입됨 (Supabase 또는 S3)
    private final SellerConcertRepository sellerConcertRepository;
    private final SellerApprovalHistoryRepository sellerApprovalHistoryRepository;

    // 환경별 버킷 설정 (application.yml에서 주입)
    @Value("${supabase.docs-bucket:#{null}}")
    private String supabaseDocsBucket;

    @Value("${cloud.aws.s3.bucket:#{null}}")
    private String s3Bucket;

    /**
     * API-03-06: 판매자 권한 신청 등록/재신청
     * 판매자 권한이 없는 사용자가 판매자 권한을 요청합니다.
     * canReapply = true 일 때 활성화됩니다.
     *
     * @param userId   신청 사용자 ID
     * @param request  판매자 신청 정보 (업체명, 사업자번호 등)
     * @param document 제출 서류 파일
     * @throws BusinessException 이미 신청했거나, 판매자 권한을 이미 가진 경우 등 비즈니스 예외
     * @return void // 성공 시 반환 값 없음
     */
    @Transactional
    public void applyForSeller(Long userId, SellerApplicationRequestDTO request, MultipartFile document) {
        // 1. 유저 존재 여부 확인
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));

        // 2. 판매자 권한 신청 유효성 검사 (이미 PENDING 상태의 신청이 있는지, 또는 APPROVED 상태인지 등)
        if (user.getApprovalStatus() == ApprovalStatus.PENDING ||
                sellerApplicationRepository.existsByUserAndStatus(user, SUBMITTED)) {
            throw new BusinessException(ErrorCode.SELLER_APPLY_ONCE, "이미 판매자 권한 신청이 접수되어 처리 대기 중입니다.");
        }
        if (user.getRole() == Role.SELLER && user.getApprovalStatus() == ApprovalStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 판매자 권한을 가지고 있습니다.");
        }

        // 추가: 사업자등록번호 중복 검사 (SUBMITTED 또는 ACCEPTED 상태인 경우)
        boolean isBusinessNumberAlreadyInUse = sellerApplicationRepository.existsByBusinessNumberAndStatusIn(
                request.getBusinessNumber(),
                Arrays.asList(SUBMITTED, ACCEPTED)
        );
        if (isBusinessNumberAlreadyInUse) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 사용 중이거나 처리 대기 중인 사업자등록번호입니다.");
        }

        // 3. 제출 서류 파일 유효성 검사 (FileValidator 사용)
        FileValidator.validate(document);

        // 4. 스토리지에 문서 업로드 (환경에 따라 Supabase 또는 S3)
        String fileUuid = java.util.UUID.randomUUID().toString();
        String fileExtension = "";
        if (document.getOriginalFilename() != null && document.getOriginalFilename().contains(".")) {
            fileExtension = document.getOriginalFilename().substring(document.getOriginalFilename().lastIndexOf('.') + 1);
        }
        String filePath = UploadPathUtil.getSellerDocsPath(fileUuid, fileExtension);

        // 환경에 따른 버킷 결정
        String bucketName = getBucketNameForDocs();
        String uploadedFileUrl = storageUploader.uploadFile(document, bucketName, filePath);

        // 5. SellerApplication 엔티티 생성 및 저장
        SellerApplication sellerApplication = SellerApplication.builder()
                .user(user)
                .companyName(request.getCompanyName())
                .businessNumber(request.getBusinessNumber())
                .representativeName(request.getRepresentativeName())
                .representativePhone(request.getRepresentativePhone())
                .uploadedFileUrl(uploadedFileUrl)
                .status(SUBMITTED)
                .build();
        sellerApplicationRepository.save(sellerApplication);

        // 6. UserEntity의 approvalStatus 업데이트
        user.setApprovalStatus(ApprovalStatus.PENDING);
        userRepository.save(user);

        // 7. SellerApprovalHistory에 REQUEST 타입의 이력 기록
        SellerApprovalHistory history = SellerApprovalHistory.builder()
                .user(user)
                .sellerApplication(sellerApplication)
                .type(SellerApprovalHistory.ActionType.REQUEST)
                .reason(null)
                .build();
        sellerApprovalHistoryRepository.save(history);
    }

    /**
     * API-03-05: 로그인 사용자의 현재 권한 상태 조회
     * 로그인한 사용자의 현재 권한 상태를 조회하고 프론트엔드에 필요한 정보를 제공합니다.
     *
     * @param userId 조회할 사용자 ID
     * @return 판매자 권한 상태 응답 DTO
     * @throws BusinessException 사용자를 찾을 수 없는 경우
     */
    @Transactional(readOnly = true)
    public SellerApplicationStatusResponseDTO getSellerApplicationStatus(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));

        // 사용자 역할 및 승인 상태
        Role userRole = user.getRole();
        ApprovalStatus userApprovalStatus = user.getApprovalStatus();

        // 최신 판매자 신청서 정보 조회 (신청일 및 마지막 처리일 등)
        Optional<SellerApplication> latestApplication = sellerApplicationRepository.findTopByUserAndStatusInOrderByCreatedAtDesc(
                user, List.of(SellerApplication.SellerApplicationStatus.SUBMITTED, SellerApplication.SellerApplicationStatus.ACCEPTED, SellerApplication.SellerApplicationStatus.REJECTED, SellerApplication.SellerApplicationStatus.REVOKED, SellerApplication.SellerApplicationStatus.WITHDRAWN)
        );

        LocalDateTime applicationDate = latestApplication.map(SellerApplication::getCreatedAt).orElse(null);
        LocalDateTime lastProcessedDate = latestApplication.map(SellerApplication::getUpdatedAt).orElse(null);

        // canReapply, canWithdraw 로직 (프론트엔드 버튼 활성화 로직)
        boolean canReapply = false;
        boolean canWithdraw = false;

        if (userApprovalStatus == null) { // 판매자 신청을 한번도 하지 않은 사용자
            canReapply = true;
        } else {
            switch (userApprovalStatus) {
                case PENDING:
                    // 신청 대기 중: 재신청 불가, 철회 불가 (관리자에게 문의)
                    break;
                case REJECTED:
                case WITHDRAWN:
                case REVOKED:
                    canReapply = true; // 반려, 자발적 철회, 관리자 회수 상태는 재신청 가능
                    break;
                case APPROVED:
                    // canWithdraw 조건 강화: 진행 중이거나 예정된 콘서트가 없는지 확인 로직 추가
                    canWithdraw = !hasActiveConcertsForSeller(userId);
                    break;
            }
        }

        String lastReason = null;
        // lastReason은 REJECTED 또는 REVOKED일 경우에만 가져오고, WITHDRAWN일 경우 null
        if (userApprovalStatus == ApprovalStatus.REJECTED || userApprovalStatus == ApprovalStatus.REVOKED) {
            Optional<SellerApprovalHistory> latestRejectOrRevokeHistory = sellerApprovalHistoryRepository
                    .findTopByUserAndTypeInOrderByCreatedAtDesc(user,
                            List.of(SellerApprovalHistory.ActionType.REJECTED, SellerApprovalHistory.ActionType.REVOKED));
            lastReason = latestRejectOrRevokeHistory.map(SellerApprovalHistory::getReason).orElse(null);
        }
        // WITHDRAWN 상태일 때는 lastReason을 명시적으로 null로 설정
        else if (userApprovalStatus == ApprovalStatus.WITHDRAWN) {
            lastReason = null;
        }

        return SellerApplicationStatusResponseDTO.builder()
                .role(userRole)
                .approvalStatus(userApprovalStatus)
                .lastReason(lastReason)
                .canReapply(canReapply)
                .canWithdraw(canWithdraw)
                .applicationDate(applicationDate)
                .lastProcessedDate(lastProcessedDate)
                .build();
    }

    /**
     * API-03-07: 판매자 본인 권한 철회
     * 판매자 본인이 자신의 권한을 자발적으로 철회하는 요청입니다. 관리자 승인이 불필요합니다.
     *
     * @param userId 철회할 사용자 ID
     * @throws BusinessException 철회할 수 없는 상태이거나 권한이 없는 경우
     */
    @Transactional
    public void withdrawSellerRole(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));

        // 1. 현재 판매자 권한이 APPROVED 상태인지 확인
        if (user.getRole() != Role.SELLER || user.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "판매자 권한이 승인된 상태가 아니므로 철회할 수 없습니다.");
        }

        // 2. canWithdraw 조건 강화 (진행 중이거나 예정된 콘서트가 없는지 확인)
        // 만약 콘서트가 있다면 철회 불가 예외 발생
        if (hasActiveConcertsForSeller(userId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "진행 중이거나 예정된 콘서트가 있어 판매자 권한을 철회할 수 없습니다.");
        }

        // 3. UserEntity의 역할(Role) 및 승인 상태(ApprovalStatus) 업데이트
        user.setRole(Role.USER);
        user.setApprovalStatus(ApprovalStatus.WITHDRAWN);
        userRepository.save(user);

        // 4. 해당 유저의 가장 최근 APPROVED 상태의 SellerApplication 상태 업데이트 (WITHDRAWN)
        Optional<SellerApplication> latestApprovedApplication = sellerApplicationRepository.findTopByUserAndStatusInOrderByCreatedAtDesc(
                user, List.of(ACCEPTED));
        latestApprovedApplication.ifPresent(app -> {
            app.setStatus(WITHDRAWN);
            sellerApplicationRepository.save(app);

            // 4. SellerApprovalHistory에 WITHDRAW 로그 기록
            SellerApprovalHistory history = SellerApprovalHistory.builder()
                    .user(user)
                    .sellerApplication(app)
                    .type(SellerApprovalHistory.ActionType.WITHDRAWN)
                    .reason(null)
                    .build();
            sellerApprovalHistoryRepository.save(history);
        });
    }

    /**
     * 사용자의 신청자 관련 상세 정보를 조회합니다.
     * 컨트롤러에서 직접 리포지토리를 사용하는 대신 서비스 계층을 통해 조회합니다.
     * @param userId 조회할 사용자 ID
     * @return ApplicantInformationResponseDTO
     * @throws BusinessException 사용자를 찾을 수 없는 경우
     */
    @Transactional(readOnly = true)
    public ApplicantInformationResponseDTO getUserApplicantInfo(Long userId) {
        UserEntity userEntity = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "사용자 정보를 찾을 수 없습니다."));
        return ApplicantInformationResponseDTO.fromEntity(userEntity);
    }

    /**
     * 환경에 따른 문서 저장용 버킷명 결정
     * Supabase 환경에서는 supabase.docs-bucket 값을,
     * S3 환경에서는 cloud.aws.s3.bucket 값을 사용합니다.
     *
     * @return 환경에 맞는 버킷명
     */
    private String getBucketNameForDocs() {
        if (supabaseDocsBucket != null && !supabaseDocsBucket.isEmpty()) {
            // Supabase 환경
            return supabaseDocsBucket;
        } else if (s3Bucket != null && !s3Bucket.isEmpty()) {
            // S3 환경
            return s3Bucket;
        } else {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "스토리지 설정이 올바르지 않습니다.");
        }
    }

    /**
     * 판매자가 진행 중이거나 예정된 콘서트(ON_SALE, SCHEDULED)를 가지고 있는지 확인합니다.
     * 판매자 권한 철회 시 제약 조건으로 사용됩니다.
     * @param sellerId 판매자 ID
     * @return 활성 콘서트가 있으면 true, 없으면 false
     */
    private boolean hasActiveConcertsForSeller(Long sellerId) {
        // ON_SALE 또는 SCHEDULED 상태의 콘서트가 있는지 확인합니다.
        List<ConcertStatus> activeStatuses = List.of(ConcertStatus.ON_SALE, ConcertStatus.SCHEDULED);
        List<Concert> activeConcerts = sellerConcertRepository.findBySellerIdAndStatusIn(sellerId, activeStatuses);

        return !activeConcerts.isEmpty(); // 비어있지 않으면 활성 콘서트가 있는 것
    }
}