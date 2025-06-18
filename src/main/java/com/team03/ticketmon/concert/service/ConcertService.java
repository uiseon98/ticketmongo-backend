package com.team03.ticketmon.concert.service;

import com.team03.ticketmon.concert.dto.ConcertDTO;
import com.team03.ticketmon.concert.dto.ConcertFilterDTO;
import com.team03.ticketmon.concert.dto.ConcertSearchDTO;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.enums.ConcertStatus;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.concert.repository.ConcertSeatRepository;
import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Concert Service
 * 콘서트 비즈니스 로직 처리
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConcertService {

	private final ConcertRepository concertRepository;
	private final ConcertSeatRepository concertSeatRepository;

	// 상수로 추출하여 중복 제거
	private static final List<ConcertStatus> ACTIVE_STATUSES = Arrays.asList(
		ConcertStatus.SCHEDULED,
		ConcertStatus.ON_SALE
	);

	// 페이징 관련 상수
	private static final int MIN_PAGE = 0;
	private static final int MIN_SIZE = 1;
	private static final int MAX_SIZE = 100;
	private static final int DEFAULT_SIZE = 20;

	/**
	 * Retrieves a paginated list of concerts with active statuses.
	 *
	 * @param page the page number to retrieve
	 * @param size the number of concerts per page
	 * @return a page of active concert DTOs
	 */
	public Page<ConcertDTO> getAllConcerts(int page, int size) {
		// 페이징 파라미터 검증
		validatePagingParameters(page, size);

		Pageable pageable = PageRequest.of(page, size);
		return getConcertsByStatuses(ACTIVE_STATUSES, pageable);
	}

	/**
	 * Retrieves all concerts with active statuses without applying pagination.
	 *
	 * @return a list of concert DTOs representing all active concerts
	 */
	public List<ConcertDTO> getAllConcertsWithoutPaging() {
		return getConcertsByStatuses(ACTIVE_STATUSES);
	}

	/**
	 * Retrieves a paginated list of concerts filtered by the specified status.
	 *
	 * @param status the concert status to filter by
	 * @param pageable the pagination information
	 * @return a page of concerts matching the given status
	 * @throws BusinessException if the status is null
	 */
	public Page<ConcertDTO> getConcertsWithPaging(ConcertStatus status, Pageable pageable) {
		if (status == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}

		Page<Concert> concertPage = concertRepository
			.findByStatusOrderByConcertDateAsc(status, pageable);
		return concertPage.map(this::convertToDTO);
	}

	/**
	 * Searches for concerts that match the given keyword.
	 *
	 * @param keyword the search keyword; must not be null or empty
	 * @return a list of concerts whose attributes match the keyword
	 */
	public List<ConcertDTO> searchByKeyword(String keyword) {
		validateKeyword(keyword);

		return concertRepository
			.findByKeyword(keyword.trim())
			.stream()
			.map(this::convertToDTO)
			.collect(Collectors.toList());
	}

	/**
	 * Returns a list of concerts occurring within the specified date range.
	 *
	 * @param startDate the start date of the range (inclusive)
	 * @param endDate the end date of the range (inclusive)
	 * @return a list of concerts scheduled between the given dates
	 */
	public List<ConcertDTO> filterByDateRange(LocalDate startDate, LocalDate endDate) {
		validateDateRange(startDate, endDate);

		return concertRepository
			.findByDateRange(startDate, endDate)
			.stream()
			.map(this::convertToDTO)
			.collect(Collectors.toList());
	}

	/**
	 * Returns a list of concerts whose prices fall within the specified range.
	 *
	 * @param minPrice the minimum price (inclusive)
	 * @param maxPrice the maximum price (inclusive)
	 * @return a list of concerts matching the given price range
	 */
	public List<ConcertDTO> filterByPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
		validatePriceRange(minPrice, maxPrice);

		return concertRepository
			.findByPriceRange(minPrice, maxPrice)
			.stream()
			.map(this::convertToDTO)
			.collect(Collectors.toList());
	}

	/**
	 * Filters concerts within the specified date and price ranges.
	 *
	 * @param startDate the start date of the concert range (inclusive)
	 * @param endDate the end date of the concert range (inclusive)
	 * @param minPrice the minimum ticket price (inclusive)
	 * @param maxPrice the maximum ticket price (inclusive)
	 * @return a list of concerts matching the given date and price criteria
	 */
	public List<ConcertDTO> filterByDateAndPriceRange(
		LocalDate startDate, LocalDate endDate,
		BigDecimal minPrice, BigDecimal maxPrice) {

		validateDateRange(startDate, endDate);
		validatePriceRange(minPrice, maxPrice);

		return concertRepository
			.findByDateAndPriceRange(startDate, endDate, minPrice, maxPrice)
			.stream()
			.map(this::convertToDTO)
			.collect(Collectors.toList());
	}

	/**
	 * Returns a list of concerts filtered by the criteria specified in the given filter DTO.
	 *
	 * If the filter DTO is null or contains no filter criteria, all concerts with active statuses are returned.
	 * Filters can include date range, price range, or both.
	 *
	 * @param filterDTO the filter criteria for concerts, including optional date and price ranges
	 * @return a list of concerts matching the specified filters, or all active concerts if no filters are provided
	 */
	public List<ConcertDTO> applyFilters(ConcertFilterDTO filterDTO) {
		if (filterDTO == null) {
			return getAllConcertsWithoutPaging();
		}

		LocalDate startDate = filterDTO.getStartDate();
		LocalDate endDate = filterDTO.getEndDate();
		BigDecimal priceMin = filterDTO.getPriceMin();
		BigDecimal priceMax = filterDTO.getPriceMax();

		boolean hasDateFilter = startDate != null || endDate != null;
		boolean hasPriceFilter = priceMin != null || priceMax != null;

		if (hasDateFilter && hasPriceFilter) {
			return filterByDateAndPriceRange(startDate, endDate, priceMin, priceMax);
		} else if (hasDateFilter) {
			return filterByDateRange(startDate, endDate);
		} else if (hasPriceFilter) {
			return filterByPriceRange(priceMin, priceMax);
		} else {
			return getAllConcertsWithoutPaging();
		}
	}

	/**
	 * 날짜별 콘서트 조회
	 */
	public List<ConcertDTO> getConcertsByDate(LocalDate concertDate) {
		if (concertDate == null) {
			throw new BusinessException(ErrorCode.CONCERT_DATE_REQUIRED);
		}

		return concertRepository
			.findByConcertDateAndStatusOrderByConcertDateAsc(concertDate, ConcertStatus.ON_SALE)
			.stream()
			.map(this::convertToDTO)
			.collect(Collectors.toList());
	}

	/**
	 * 예매 가능한 콘서트 조회
	 */
	public List<ConcertDTO> getBookableConcerts() {
		return concertRepository
			.findBookableConcerts()
			.stream()
			.map(this::convertToDTO)
			.collect(Collectors.toList());
	}

	/**
	 * 콘서트 검색 (DTO 기반)
	 */
	public List<ConcertDTO> searchConcerts(ConcertSearchDTO searchDTO) {
		if (searchDTO == null || searchDTO.getKeyword() == null) {
			throw new BusinessException(ErrorCode.SEARCH_CONDITION_REQUIRED);
		}

		return searchByKeyword(searchDTO.getKeyword());
	}

	/**
	 * Retrieves a concert by its ID and returns it as an optional DTO.
	 *
	 * @param id the unique identifier of the concert
	 * @return an Optional containing the ConcertDTO if found, or empty if not found
	 */
	public Optional<ConcertDTO> getConcertById(Long id) {
		validateConcertId(id);

		return concertRepository.findById(id)
			.map(this::convertToDTO);
	}

	/**
	 * Retrieves the AI-generated summary for a concert by its ID.
	 *
	 * If no summary exists, returns a default message indicating that the AI summary has not yet been generated.
	 *
	 * @param id the unique identifier of the concert
	 * @return the AI-generated summary, or a default message if unavailable
	 */
	public String getAiSummary(Long id) {
		validateConcertId(id);

		return concertRepository.findById(id)
			.map(Concert::getAiSummary)
			.filter(summary -> summary != null && !summary.trim().isEmpty())
			.orElse("AI 요약 정보가 아직 생성되지 않았습니다.");
	}

	// ========== Private Helper Methods ==========

	/**
	 * Retrieves a paginated list of concerts filtered by the specified statuses.
	 *
	 * @param statuses the list of concert statuses to filter by
	 * @param pageable the pagination information
	 * @return a page of concert DTOs matching the given statuses
	 */
	private Page<ConcertDTO> getConcertsByStatuses(List<ConcertStatus> statuses, Pageable pageable) {
		Page<Concert> concertPage = concertRepository
			.findByStatusInOrderByConcertDateAsc(statuses, pageable);

		return concertPage.map(this::convertToDTO);
	}

	/**
	 * Retrieves a list of concerts filtered by the specified statuses, ordered by concert date in ascending order.
	 *
	 * @param statuses the list of concert statuses to filter by
	 * @return a list of concert DTOs matching the given statuses
	 */
	private List<ConcertDTO> getConcertsByStatuses(List<ConcertStatus> statuses) {
		return concertRepository
			.findByStatusInOrderByConcertDateAsc(statuses)
			.stream()
			.map(this::convertToDTO)
			.collect(Collectors.toList());
	}

	/**
	 * Validates that the paging parameters are within allowed bounds.
	 *
	 * @param page the page number to validate
	 * @param size the page size to validate
	 * @throws BusinessException if the page number or size is outside the allowed range
	 */
	private void validatePagingParameters(int page, int size) {
		if (page < MIN_PAGE) {
			throw new BusinessException(ErrorCode.INVALID_PAGE_NUMBER);
		}
		if (size < MIN_SIZE || size > MAX_SIZE) {
			throw new BusinessException(ErrorCode.INVALID_PAGE_SIZE);
		}
	}

	/**
	 * Validates that the provided keyword is not null or empty.
	 *
	 * @param keyword the search keyword to validate
	 * @throws BusinessException if the keyword is null or empty
	 */
	private void validateKeyword(String keyword) {
		if (keyword == null || keyword.trim().isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_SEARCH_KEYWORD);
		}
	}

	/**
	 * Validates that the start date is not after the end date.
	 *
	 * @param startDate the start date to validate
	 * @param endDate the end date to validate
	 * @throws BusinessException if both dates are provided and the start date is after the end date
	 */
	private void validateDateRange(LocalDate startDate, LocalDate endDate) {
		if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
			throw new BusinessException(ErrorCode.INVALID_DATE_ORDER);
		}
	}

	/**
	 * Validates that the provided price range is non-negative and that the minimum price does not exceed the maximum price.
	 *
	 * @param minPrice the minimum price to validate
	 * @param maxPrice the maximum price to validate
	 * @throws BusinessException if either price is negative or if minPrice is greater than maxPrice
	 */
	private void validatePriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
		if (minPrice != null && minPrice.compareTo(BigDecimal.ZERO) < 0) {
			throw new BusinessException(ErrorCode.INVALID_PRICE_RANGE);
		}
		if (maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) < 0) {
			throw new BusinessException(ErrorCode.INVALID_PRICE_RANGE);
		}
		if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
			throw new BusinessException(ErrorCode.INVALID_PRICE_RANGE);
		}
	}

	/**
	 * Validates that the concert ID is not null and greater than zero.
	 *
	 * @param id the concert ID to validate
	 * @throws BusinessException if the ID is null or not positive
	 */
	private void validateConcertId(Long id) {
		if (id == null || id <= 0) {
			throw new BusinessException(ErrorCode.INVALID_CONCERT_ID);
		}
	}

	/**
	 * Converts a Concert entity to a ConcertDTO by mapping all relevant fields.
	 *
	 * @param concert the Concert entity to convert
	 * @return a ConcertDTO containing the mapped data from the entity
	 */
	private ConcertDTO convertToDTO(Concert concert) {
		return new ConcertDTO(
			concert.getConcertId(),
			concert.getTitle(),
			concert.getArtist(),
			concert.getDescription(),
			concert.getSellerId(),
			concert.getVenueName(),
			concert.getVenueAddress(),
			concert.getConcertDate(),
			concert.getStartTime(),
			concert.getEndTime(),
			concert.getTotalSeats(),
			concert.getBookingStartDate(),
			concert.getBookingEndDate(),
			concert.getMinAge(),
			concert.getMaxTicketsPerUser(),
			concert.getStatus(),
			concert.getPosterImageUrl(),
			concert.getAiSummary()
		);
	}
}