package com.pm.connecto.language.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pm.connecto.common.exception.DuplicateResourceException;
import com.pm.connecto.common.exception.MaxLimitExceededException;
import com.pm.connecto.common.exception.ResourceNotFoundException;
import com.pm.connecto.common.response.ErrorCode;
import com.pm.connecto.language.domain.Language;
import com.pm.connecto.language.domain.LanguageLevel;
import com.pm.connecto.language.domain.LanguageType;
import com.pm.connecto.language.dto.LanguageRequest.LanguageItem;
import com.pm.connecto.language.repository.LanguageRepository;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.repository.UserRepository;

@Service
public class LanguageService {

	private static final int MAX_LANGUAGES_PER_USER = 10;

	private final LanguageRepository languageRepository;
	private final UserRepository userRepository;

	public LanguageService(LanguageRepository languageRepository, UserRepository userRepository) {
		this.languageRepository = languageRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public Language addLanguage(Long userId, String languageCode, LanguageType type, LanguageLevel level) {
		User user = userRepository.findActiveById(userId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

		if (languageRepository.existsByUserIdAndLanguageCodeAndType(userId, languageCode, type)) {
			throw new DuplicateResourceException(ErrorCode.DUPLICATE_LANGUAGE);
		}

		int currentCount = languageRepository.countByUserId(userId);
		if (currentCount >= MAX_LANGUAGES_PER_USER) {
			throw new MaxLimitExceededException(ErrorCode.MAX_LIMIT_EXCEEDED,
				"최대 " + MAX_LANGUAGES_PER_USER + "개까지만 등록할 수 있습니다.");
		}

		Language language = Language.builder()
			.user(user)
			.languageCode(languageCode)
			.type(type)
			.level(level)
			.build();

		return languageRepository.save(language);
	}

	@Transactional(readOnly = true)
	public List<Language> getLanguages(Long userId) {
		return languageRepository.findByUserId(userId);
	}

	@Transactional(readOnly = true)
	public List<Language> getLanguagesByType(Long userId, LanguageType type) {
		return languageRepository.findByUserIdAndType(userId, type);
	}

	@Transactional
	public void deleteLanguage(Long userId, Long languageId) {
		Language language = languageRepository.findByIdAndUserId(languageId, userId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LANGUAGE_NOT_FOUND));

		languageRepository.delete(language);
	}

	@Transactional
	public Language updateLanguageLevel(Long userId, Long languageId, LanguageLevel level) {
		Language language = languageRepository.findByIdAndUserId(languageId, userId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LANGUAGE_NOT_FOUND));

		language.updateLevel(level);
		return language;
	}

	/**
	 * 사용자 언어 전체 교체 (기존 삭제 후 새로 저장)
	 * 
	 * <p>동작 순서:
	 * <ol>
	 *   <li>사용자 존재 여부 확인 (ACTIVE 상태)</li>
	 *   <li>요청 데이터 검증 (개수 제한, 중복 체크)</li>
	 *   <li>기존 언어 전체 삭제 (JPQL DELETE → 영속성 컨텍스트 클리어)</li>
	 *   <li>User 프록시 재생성 (DETACHED 문제 방지)</li>
	 *   <li>새 언어 저장</li>
	 * </ol>
	 * 
	 * <p>Flush 전략:
	 * <ul>
	 *   <li>deleteByUserId: flushAutomatically + clearAutomatically 적용</li>
	 *   <li>DELETE 실행 후 getReferenceById로 User 프록시 재생성</li>
	 * </ul>
	 */
	@Transactional
	public List<Language> replaceLanguages(Long userId, List<LanguageItem> languageItems) {
		// 1. 사용자 존재 여부 검증 (ACTIVE 상태만)
		if (userRepository.findActiveById(userId).isEmpty()) {
			throw new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND);
		}

		// 2. 개수 제한 검증
		if (languageItems.size() > MAX_LANGUAGES_PER_USER) {
			throw new MaxLimitExceededException(ErrorCode.MAX_LIMIT_EXCEEDED,
				"최대 " + MAX_LANGUAGES_PER_USER + "개까지만 등록할 수 있습니다.");
		}

		// 3. 중복 검증 (같은 languageCode + type 조합)
		long uniqueCount = languageItems.stream()
			.map(item -> item.languageCode() + "_" + item.type())
			.distinct()
			.count();
		if (uniqueCount != languageItems.size()) {
			throw new DuplicateResourceException(ErrorCode.DUPLICATE_LANGUAGE);
		}

		// 4. 기존 언어 전체 삭제 (JPQL DELETE → 영속성 컨텍스트 클리어됨)
		languageRepository.deleteByUserId(userId);

		// 5. User 프록시 재생성 (DETACHED 문제 방지)
		// getReferenceById는 DB 조회 없이 프록시 객체 반환 (MANAGED 상태)
		User userProxy = userRepository.getReferenceById(userId);

		// 6. 새 언어 저장
		List<Language> newLanguages = languageItems.stream()
			.map(item -> Language.builder()
				.user(userProxy)
				.languageCode(item.languageCode())
				.type(item.type())
				.level(item.level())
				.build())
			.toList();

		return languageRepository.saveAll(newLanguages);
	}

	// ========== 매칭용 메서드 ==========

	@Transactional(readOnly = true)
	public List<Long> findMatchingUsers(Long userId) {
		List<Language> nativeLanguages = languageRepository.findByUserIdAndType(userId, LanguageType.NATIVE);
		List<Language> learningLanguages = languageRepository.findByUserIdAndType(userId, LanguageType.LEARNING);

		if (nativeLanguages.isEmpty() || learningLanguages.isEmpty()) {
			return List.of();
		}

		String myNativeCode = nativeLanguages.get(0).getLanguageCode();
		String myLearningCode = learningLanguages.get(0).getLanguageCode();

		return languageRepository.findMatchingUserIds(userId, myNativeCode, myLearningCode);
	}
}
