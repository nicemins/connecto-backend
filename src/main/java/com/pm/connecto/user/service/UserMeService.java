package com.pm.connecto.user.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pm.connecto.common.exception.ForbiddenException;
import com.pm.connecto.common.exception.ResourceNotFoundException;
import com.pm.connecto.common.exception.UnauthorizedException;
import com.pm.connecto.common.response.ErrorCode;
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

	public UserMeService(
		UserRepository userRepository,
		ProfileRepository profileRepository,
		LanguageRepository languageRepository
	) {
		this.userRepository = userRepository;
		this.profileRepository = profileRepository;
		this.languageRepository = languageRepository;
	}

	@Transactional(readOnly = true)
	public UserMeResponse getMyInfo(Long userId) {
		User user = findUserWithValidation(userId);
		Profile profile = profileRepository.findByUserId(userId).orElse(null);
		List<Language> languages = languageRepository.findByUserId(userId);

		return UserMeResponse.builder()
			.user(user)
			.profile(profile)
			.languages(languages)
			.build();
	}

	private User findUserWithValidation(Long userId) {
		User user = userRepository.findByIdForAuth(userId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

		if (user.getDeletedAt() != null) {
			throw new UnauthorizedException(ErrorCode.DELETED_USER);
		}

		if (!user.isActive()) {
			if (user.isBlocked()) {
				throw new ForbiddenException(ErrorCode.BLOCKED_USER);
			}
			throw new ForbiddenException(ErrorCode.INACTIVE_USER);
		}

		return user;
	}

	@Transactional(readOnly = true)
	public UserMeResponse getUserProfile(Long targetUserId) {
		User user = findUserWithValidation(targetUserId);
		Profile profile = profileRepository.findByUserId(targetUserId).orElse(null);
		List<Language> languages = languageRepository.findByUserId(targetUserId);

		return UserMeResponse.builder()
			.user(user)
			.profile(profile)
			.languages(languages)
			.build();
	}
}
