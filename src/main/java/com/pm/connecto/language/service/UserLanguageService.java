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
import com.pm.connecto.language.domain.UserLanguage;
import com.pm.connecto.language.repository.LanguageRepository;
import com.pm.connecto.language.repository.UserLanguageRepository;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.repository.UserRepository;

@Service
public class UserLanguageService {

	private static final int MAX_LANGUAGES_PER_USER = 10;

	private final UserLanguageRepository userLanguageRepository;
	private final LanguageRepository languageRepository;
	private final UserRepository userRepository;

	public UserLanguageService(
		UserLanguageRepository userLanguageRepository,
		LanguageRepository languageRepository,
		UserRepository userRepository
	) {
		this.userLanguageRepository = userLanguageRepository;
		this.languageRepository = languageRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public UserLanguage addUserLanguage(Long userId, String languageCode, LanguageType type, LanguageLevel level) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

		Language language = languageRepository.findByCode(languageCode)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LANGUAGE_NOT_FOUND));

		if (userLanguageRepository.existsByUserIdAndLanguageIdAndType(userId, language.getId(), type)) {
			throw new DuplicateResourceException(ErrorCode.DUPLICATE_LANGUAGE);
		}

		int currentCount = userLanguageRepository.countByUserId(userId);
		if (currentCount >= MAX_LANGUAGES_PER_USER) {
			throw new MaxLimitExceededException(ErrorCode.MAX_LIMIT_EXCEEDED,
				"최대 " + MAX_LANGUAGES_PER_USER + "개까지만 등록할 수 있습니다.");
		}

		UserLanguage userLanguage = new UserLanguage(user, language, type, level);
		return userLanguageRepository.save(userLanguage);
	}

	@Transactional(readOnly = true)
	public List<UserLanguage> getUserLanguages(Long userId) {
		return userLanguageRepository.findByUserIdWithLanguage(userId);
	}

	@Transactional
	public void deleteUserLanguage(Long userId, Long userLanguageId) {
		UserLanguage userLanguage = userLanguageRepository.findByIdAndUserId(userLanguageId, userId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

		userLanguageRepository.delete(userLanguage);
	}
}
