package com.pm.connecto.user.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pm.connecto.common.exception.ForbiddenException;
import com.pm.connecto.common.exception.ResourceNotFoundException;
import com.pm.connecto.common.exception.UnauthorizedException;
import com.pm.connecto.common.response.ErrorCode;
import com.pm.connecto.interest.domain.Interest;
import com.pm.connecto.interest.domain.UserInterest;
import com.pm.connecto.interest.repository.UserInterestRepository;
import com.pm.connecto.language.domain.Language;
import com.pm.connecto.language.repository.LanguageRepository;
import com.pm.connecto.profile.domain.Profile;
import com.pm.connecto.profile.repository.ProfileRepository;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.dto.UserMeResponse;
import com.pm.connecto.user.repository.UserRepository;

@Service
public class UserMeService {

	private final UserRepository userRepository;
	private final ProfileRepository profileRepository;
	private final LanguageRepository languageRepository;
	private final UserInterestRepository userInterestRepository;

	public UserMeService(
		UserRepository userRepository,
		ProfileRepository profileRepository,
		LanguageRepository languageRepository,
		UserInterestRepository userInterestRepository
	) {
		this.userRepository = userRepository;
		this.profileRepository = profileRepository;
		this.languageRepository = languageRepository;
		this.userInterestRepository = userInterestRepository;
	}

	/**
	 * 사용자 통합 정보 조회
	 * - User + Profile + Languages + Interests 한 번에 조회
	 * - 탈퇴/차단 사용자 예외 처리
	 * - N+1 방지: 각 엔티티별 단일 쿼리 실행 (총 4개 쿼리)
	 */
	@Transactional(readOnly = true)
	public UserMeResponse getMyInfo(Long userId) {
		// 1. User 조회 (상태 무관하게 조회 후 검증)
		User user = findUserWithValidation(userId);

		// 2. 연관 데이터 병렬 조회 (N+1 방지 - 각각 단일 쿼리)
		Profile profile = profileRepository.findByUserId(userId).orElse(null);
		List<Language> languages = languageRepository.findByUserId(userId);
		List<Interest> interests = fetchInterests(userId);

		// 3. DTO 조립
		return UserMeResponse.builder()
			.user(user)
			.profile(profile)
			.languages(languages)
			.interests(interests)
			.build();
	}

	/**
	 * 사용자 조회 + 상태 검증
	 * - 존재하지 않는 사용자: 404
	 * - 탈퇴한 사용자 (deletedAt != null): 401
	 * - 차단된 사용자: 403
	 * - 비활성 사용자: 403
	 */
	private User findUserWithValidation(Long userId) {
		User user = userRepository.findByIdForAuth(userId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

		// 탈퇴한 사용자 체크 (deletedAt 우선 확인)
		if (user.getDeletedAt() != null) {
			throw new UnauthorizedException(ErrorCode.DELETED_USER);
		}

		// 비활성 상태 체크
		if (!user.isActive()) {
			if (user.isBlocked()) {
				throw new ForbiddenException(ErrorCode.BLOCKED_USER);
			}
			throw new ForbiddenException(ErrorCode.INACTIVE_USER);
		}

		return user;
	}

	/**
	 * 관심사 조회 (N+1 방지: JOIN FETCH 사용)
	 */
	private List<Interest> fetchInterests(Long userId) {
		return userInterestRepository.findByUserIdWithInterest(userId).stream()
			.map(UserInterest::getInterest)
			.toList();
	}

	/**
	 * 다른 사용자 프로필 조회 (공개 정보만)
	 * - 탈퇴/차단 사용자 제외
	 */
	@Transactional(readOnly = true)
	public UserMeResponse getUserProfile(Long targetUserId) {
		User user = findUserWithValidation(targetUserId);

		Profile profile = profileRepository.findByUserId(targetUserId).orElse(null);
		List<Language> languages = languageRepository.findByUserId(targetUserId);
		List<Interest> interests = fetchInterests(targetUserId);

		return UserMeResponse.builder()
			.user(user)
			.profile(profile)
			.languages(languages)
			.interests(interests)
			.build();
	}
}
