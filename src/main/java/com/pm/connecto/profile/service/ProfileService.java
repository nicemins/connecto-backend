package com.pm.connecto.profile.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pm.connecto.common.exception.DuplicateResourceException;
import com.pm.connecto.common.exception.ResourceNotFoundException;
import com.pm.connecto.common.response.ErrorCode;
import com.pm.connecto.profile.domain.Profile;
import com.pm.connecto.profile.repository.ProfileRepository;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.repository.UserRepository;

@Service
public class ProfileService {

	private final ProfileRepository profileRepository;
	private final UserRepository userRepository;

	public ProfileService(ProfileRepository profileRepository, UserRepository userRepository) {
		this.profileRepository = profileRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public Profile createProfile(Long userId, String nickname, String profileImageUrl, String bio) {
		User user = userRepository.findActiveById(userId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

		if (profileRepository.existsByUserId(userId)) {
			throw new DuplicateResourceException(ErrorCode.DUPLICATE_PROFILE);
		}

		if (profileRepository.existsByNickname(nickname)) {
			throw new DuplicateResourceException(ErrorCode.DUPLICATE_NICKNAME);
		}

		Profile profile = Profile.builder()
			.user(user)
			.nickname(nickname)
			.profileImageUrl(profileImageUrl)
			.bio(bio)
			.build();

		return profileRepository.save(profile);
	}

	@Transactional(readOnly = true)
	public Profile getProfile(Long userId) {
		return profileRepository.findByUserId(userId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PROFILE_NOT_FOUND));
	}

	@Transactional(readOnly = true)
	public Profile getProfileById(Long profileId) {
		return profileRepository.findById(profileId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PROFILE_NOT_FOUND));
	}

	/**
	 * 프로필 수정
	 * - 프로필이 없으면 예외 발생
	 * - nickname은 optional (null이면 변경 안 함)
	 */
	@Transactional
	public Profile updateProfile(Long userId, String nickname, String profileImageUrl, String bio) {
		Profile profile = profileRepository.findByUserId(userId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PROFILE_NOT_FOUND));

		// 닉네임 변경 시 중복 체크
		if (nickname != null && !nickname.isBlank() && !nickname.equals(profile.getNickname())) {
			if (profileRepository.existsByNicknameAndUserIdNot(nickname, userId)) {
				throw new DuplicateResourceException(ErrorCode.DUPLICATE_NICKNAME);
			}
		}

		profile.update(nickname, profileImageUrl, bio);
		return profile;
	}

	@Transactional(readOnly = true)
	public boolean isNicknameAvailable(String nickname) {
		return !profileRepository.existsByNickname(nickname);
	}

	@Transactional(readOnly = true)
	public boolean hasProfile(Long userId) {
		return profileRepository.existsByUserId(userId);
	}
}
