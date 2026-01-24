package com.pm.connecto.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.pm.connecto.auth.jwt.JwtTokenProvider;
import com.pm.connecto.common.exception.ForbiddenException;
import com.pm.connecto.common.exception.ResourceNotFoundException;
import com.pm.connecto.common.exception.UnauthorizedException;
import com.pm.connecto.common.response.ErrorCode;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.repository.UserRepository;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final JwtTokenProvider jwtTokenProvider;
	private final PasswordEncoder passwordEncoder;

	public AuthService(UserRepository userRepository, JwtTokenProvider jwtTokenProvider, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.jwtTokenProvider = jwtTokenProvider;
		this.passwordEncoder = passwordEncoder;
	}

	public User authenticate(String email, String password) {
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

		return user;
	}

	public String generateAccessToken(Long userId) {
		return jwtTokenProvider.generateAccessToken(userId);
	}

	public String generateRefreshToken(Long userId) {
		return jwtTokenProvider.generateRefreshToken(userId);
	}

	public String refreshAccessToken(String refreshToken) {
		if (!jwtTokenProvider.validateToken(refreshToken)) {
			throw new UnauthorizedException(ErrorCode.INVALID_TOKEN);
		}

		Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);

		User user = userRepository.findByIdForAuth(userId)
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

		return jwtTokenProvider.generateAccessToken(userId);
	}

	public long getRefreshExpiration() {
		return jwtTokenProvider.getRefreshExpiration();
	}
}
