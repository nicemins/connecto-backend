package com.pm.connecto.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pm.connecto.auth.jwt.JwtTokenProvider;
import com.pm.connecto.common.exception.DuplicateResourceException;
import com.pm.connecto.common.exception.ForbiddenException;
import com.pm.connecto.common.exception.ResourceNotFoundException;
import com.pm.connecto.common.exception.UnauthorizedException;
import com.pm.connecto.common.response.ErrorCode;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.repository.UserRepository;

@Service
public class UserService {

	private final UserRepository userRepository;
	private final JwtTokenProvider jwtTokenProvider;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository userRepository, JwtTokenProvider jwtTokenProvider, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.jwtTokenProvider = jwtTokenProvider;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public User createUser(String email, String password) {
		if (userRepository.existsByEmail(email)) {
			throw new DuplicateResourceException(ErrorCode.DUPLICATE_EMAIL);
		}

		String encodedPassword = passwordEncoder.encode(password);
		User user = new User(email, encodedPassword);
		return userRepository.save(user);
	}

	public String login(String email, String password) {
		User user = userRepository.findByEmailForAuth(email)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

		// 1. deletedAt 확인 (Soft Delete)
		if (user.getDeletedAt() != null) {
			throw new UnauthorizedException(ErrorCode.DELETED_USER);
		}

		// 2. status != ACTIVE 확인
		if (!user.isActive()) {
			if (user.isBlocked()) {
				throw new ForbiddenException(ErrorCode.BLOCKED_USER);
			}
			throw new ForbiddenException(ErrorCode.INACTIVE_USER);
		}

		// 3. 비밀번호 확인
		if (!passwordEncoder.matches(password, user.getPassword())) {
			throw new UnauthorizedException(ErrorCode.INVALID_PASSWORD);
		}

		return jwtTokenProvider.generateAccessToken(user.getId());
	}

	@Transactional(readOnly = true)
	public User getMe(Long userId) {
		return userRepository.findActiveById(userId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
	}

	public boolean isEmailAvailable(String email) {
		return !userRepository.existsByEmail(email);
	}

	@Transactional
	public User updateUser(Long userId, String password) {
		User user = userRepository.findActiveById(userId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

		if (password != null && !password.isBlank()) {
			String encodedPassword = passwordEncoder.encode(password);
			user.updatePassword(encodedPassword);
		}

		return user;
	}

	@Transactional
	public void deleteUser(Long userId) {
		User user = userRepository.findActiveById(userId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

		user.delete();
	}
}
