package com.pm.connecto.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

	/**
	 * 사용자 인증 (로그인)
	 * 
	 * <p>트랜잭션: readOnly
	 * - DB 조회만 수행 (User 조회)
	 * - 데이터 수정 없음
	 * - 읽기 전용 트랜잭션으로 성능 최적화
	 */
	@Transactional(readOnly = true)
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

	/**
	 * Access Token 생성
	 * 
	 * <p>트랜잭션: 불필요
	 * - DB 접근 없음
	 * - JWT 토큰 생성만 수행
	 */
	public String generateAccessToken(Long userId) {
		return jwtTokenProvider.generateAccessToken(userId);
	}

	/**
	 * Refresh Token 생성
	 * 
	 * <p>트랜잭션: 불필요
	 * - DB 접근 없음
	 * - JWT 토큰 생성만 수행
	 */
	public String generateRefreshToken(Long userId) {
		return jwtTokenProvider.generateRefreshToken(userId);
	}

	/**
	 * Refresh Token으로 Access Token 재발급
	 * 
	 * <p>트랜잭션: readOnly
	 * - DB 조회만 수행 (User 조회)
	 * - 데이터 수정 없음
	 * - 사용자 상태 검증 후 새 토큰 발급
	 */
	@Transactional(readOnly = true)
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

	/**
	 * Refresh Token 만료 시간 조회
	 * 
	 * <p>트랜잭션: 불필요
	 * - DB 접근 없음
	 * - 설정값 반환만 수행
	 */
	public long getRefreshExpiration() {
		return jwtTokenProvider.getRefreshExpiration();
	}
}
