package com.pm.connecto.interest.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pm.connecto.common.exception.DuplicateResourceException;
import com.pm.connecto.common.exception.MaxLimitExceededException;
import com.pm.connecto.common.exception.ResourceNotFoundException;
import com.pm.connecto.common.response.ErrorCode;
import com.pm.connecto.interest.domain.Interest;
import com.pm.connecto.interest.domain.UserInterest;
import com.pm.connecto.interest.repository.InterestRepository;
import com.pm.connecto.interest.repository.UserInterestRepository;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.repository.UserRepository;

@Service
public class InterestService {

	private static final int MAX_INTERESTS_PER_USER = 20;

	private final InterestRepository interestRepository;
	private final UserInterestRepository userInterestRepository;
	private final UserRepository userRepository;

	public InterestService(
		InterestRepository interestRepository,
		UserInterestRepository userInterestRepository,
		UserRepository userRepository
	) {
		this.interestRepository = interestRepository;
		this.userInterestRepository = userInterestRepository;
		this.userRepository = userRepository;
	}

	// ========== Interest 마스터 데이터 ==========

	@Transactional(readOnly = true)
	public List<Interest> getAllInterests() {
		return interestRepository.findAll();
	}

	@Transactional
	public Interest createInterest(String name) {
		if (interestRepository.existsByName(name)) {
			throw new DuplicateResourceException(ErrorCode.DUPLICATE_INTEREST);
		}
		return interestRepository.save(new Interest(name));
	}

	// ========== UserInterest ==========

	@Transactional
	public List<Interest> addUserInterests(Long userId, List<String> interestNames) {
		User user = userRepository.findActiveById(userId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

		int currentCount = userInterestRepository.countByUserId(userId);
		if (currentCount + interestNames.size() > MAX_INTERESTS_PER_USER) {
			throw new MaxLimitExceededException(ErrorCode.MAX_LIMIT_EXCEEDED,
				"최대 " + MAX_INTERESTS_PER_USER + "개까지만 등록할 수 있습니다.");
		}

		List<Interest> interests = interestNames.stream()
			.map(name -> interestRepository.findByName(name)
				.orElseGet(() -> interestRepository.save(new Interest(name))))
			.toList();

		for (Interest interest : interests) {
			if (!userInterestRepository.existsByUserIdAndInterestId(userId, interest.getId())) {
				userInterestRepository.save(new UserInterest(user, interest));
			}
		}

		return interests;
	}

	@Transactional(readOnly = true)
	public List<Interest> getUserInterests(Long userId) {
		return userInterestRepository.findByUserIdWithInterest(userId).stream()
			.map(UserInterest::getInterest)
			.toList();
	}

	@Transactional
	public void removeUserInterest(Long userId, String interestName) {
		Interest interest = interestRepository.findByName(interestName)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.INTEREST_NOT_FOUND));

		UserInterest userInterest = userInterestRepository.findByUserIdAndInterestId(userId, interest.getId())
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

		userInterestRepository.delete(userInterest);
	}

	@Transactional
	public void removeUserInterestById(Long userId, Long interestId) {
		UserInterest userInterest = userInterestRepository.findByUserIdAndInterestId(userId, interestId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

		userInterestRepository.delete(userInterest);
	}

	/**
	 * 사용자 관심사 전체 교체 (기존 삭제 후 새로 저장)
	 * 
	 * <p>동작 순서:
	 * <ol>
	 *   <li>사용자 존재 여부 확인 (ACTIVE 상태)</li>
	 *   <li>요청 데이터 검증 (개수 제한)</li>
	 *   <li>중복 제거</li>
	 *   <li>기존 관심사 관계 전체 삭제 (JPQL DELETE → 영속성 컨텍스트 클리어)</li>
	 *   <li>User 프록시 재생성 (DETACHED 문제 방지)</li>
	 *   <li>Interest 조회/생성 후 UserInterest 연결</li>
	 * </ol>
	 * 
	 * <p>Flush 전략:
	 * <ul>
	 *   <li>deleteByUserId: flushAutomatically + clearAutomatically 적용</li>
	 *   <li>DELETE 실행 후 getReferenceById로 User 프록시 재생성</li>
	 * </ul>
	 */
	@Transactional
	public List<Interest> replaceUserInterests(Long userId, List<String> interestNames) {
		// 1. 사용자 존재 여부 검증 (ACTIVE 상태만)
		if (userRepository.findActiveById(userId).isEmpty()) {
			throw new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND);
		}

		// 2. 개수 제한 검증
		if (interestNames.size() > MAX_INTERESTS_PER_USER) {
			throw new MaxLimitExceededException(ErrorCode.MAX_LIMIT_EXCEEDED,
				"최대 " + MAX_INTERESTS_PER_USER + "개까지만 등록할 수 있습니다.");
		}

		// 3. 중복 제거
		List<String> uniqueNames = interestNames.stream()
			.distinct()
			.toList();

		// 4. 기존 관심사 관계 전체 삭제 (JPQL DELETE → 영속성 컨텍스트 클리어됨)
		userInterestRepository.deleteByUserId(userId);

		// 5. User 프록시 재생성 (DETACHED 문제 방지)
		// getReferenceById는 DB 조회 없이 프록시 객체 반환 (MANAGED 상태)
		User userProxy = userRepository.getReferenceById(userId);

		// 6. Interest 조회 또는 생성 후 UserInterest 연결
		List<Interest> interests = uniqueNames.stream()
			.map(name -> interestRepository.findByName(name)
				.orElseGet(() -> interestRepository.save(new Interest(name))))
			.toList();

		for (Interest interest : interests) {
			userInterestRepository.save(new UserInterest(userProxy, interest));
		}

		return interests;
	}

	// ========== 매칭용 ==========

	@Transactional(readOnly = true)
	public List<Long> findUsersWithCommonInterests(Long userId) {
		return userInterestRepository.findUserIdsWithCommonInterests(userId);
	}
}
