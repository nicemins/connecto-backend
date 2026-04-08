package com.pm.connecto.auth.service;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.pm.connecto.auth.jwt.JwtTokenProvider;
import com.pm.connecto.common.exception.BusinessException;
import com.pm.connecto.common.exception.ForbiddenException;
import com.pm.connecto.common.exception.ResourceNotFoundException;
import com.pm.connecto.common.exception.UnauthorizedException;
import com.pm.connecto.common.response.ErrorCode;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.dto.SocialLoginRequest;
import com.pm.connecto.user.repository.UserRepository;

@Service
public class AuthService {

	public record TokenPair(String accessToken, String refreshToken) {}

	private static final String REFRESH_TOKEN_KEY_PREFIX = "rt:";

	private final UserRepository userRepository;
	private final JwtTokenProvider jwtTokenProvider;
	private final PasswordEncoder passwordEncoder;

	@Autowired(required = false)
	private RedisTemplate<String, String> redisTemplate;

	@Value("${google.android-client-id:}")
	private String googleAndroidClientId;

	@Value("${google.web-client-id:}")
	private String googleWebClientId;

	private GoogleIdTokenVerifier googleIdTokenVerifier;

	@PostConstruct
	public void initGoogleVerifier() {
		List<String> audiences = Stream.of(googleAndroidClientId, googleWebClientId)
			.filter(id -> id != null && !id.isBlank()).toList();
		this.googleIdTokenVerifier = new GoogleIdTokenVerifier.Builder(
			new NetHttpTransport(), GsonFactory.getDefaultInstance())
			.setAudience(audiences).build();
	}

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
		String token = jwtTokenProvider.generateRefreshToken(userId);
		if (redisTemplate != null) {
			redisTemplate.opsForValue().set(
				REFRESH_TOKEN_KEY_PREFIX + userId,
				token,
				Duration.ofMillis(jwtTokenProvider.getRefreshExpiration())
			);
		}
		return token;
	}

	public void revokeRefreshToken(Long userId) {
		if (redisTemplate != null) {
			redisTemplate.delete(REFRESH_TOKEN_KEY_PREFIX + userId);
		}
	}

	/**
	 * Refresh Token 검증 후 Access Token + 새 Refresh Token 재발급 (rotation)
	 *
	 * <p>트랜잭션: readOnly
	 * - DB 조회만 수행 (User 조회)
	 * - Redis는 트랜잭션 외부에서 별도 처리
	 */
	@Transactional(readOnly = true)
	public TokenPair refreshAccessToken(String refreshToken) {
		if (!jwtTokenProvider.validateToken(refreshToken)) {
			throw new UnauthorizedException(ErrorCode.INVALID_TOKEN);
		}

		if (!"refresh".equals(jwtTokenProvider.getTokenType(refreshToken))) {
			throw new UnauthorizedException(ErrorCode.INVALID_TOKEN);
		}

		Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);

		if (redisTemplate != null) {
			String stored = redisTemplate.opsForValue().get(REFRESH_TOKEN_KEY_PREFIX + userId);
			if (!refreshToken.equals(stored)) {
				throw new UnauthorizedException(ErrorCode.INVALID_TOKEN);
			}
		}

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

		String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);
		if (redisTemplate != null) {
			redisTemplate.opsForValue().set(
				REFRESH_TOKEN_KEY_PREFIX + userId,
				newRefreshToken,
				Duration.ofMillis(jwtTokenProvider.getRefreshExpiration())
			);
		}
		return new TokenPair(jwtTokenProvider.generateAccessToken(userId), newRefreshToken);
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

	/**
	 * 소셜 로그인 — 이메일 추출 후 유저 조회 또는 자동 생성
	 */
	@Transactional
	public User socialLogin(SocialLoginRequest req) {
		String email = switch (req.provider()) {
			case "google" -> verifyGoogleToken(req.token());
			default -> throw new BusinessException(ErrorCode.INVALID_PROVIDER);
		};

		return userRepository.findByEmailForAuth(email)
			.filter(u -> !u.isDeleted())
			.map(u -> {
				if (!req.provider().equals(u.getProvider())) {
					throw new BusinessException(ErrorCode.INVALID_PROVIDER);
				}
				return u;
			})
			.orElseGet(() -> userRepository.save(
				User.createSocialUser(email, req.provider(), null)));
	}

	private String verifyGoogleToken(String idTokenString) {
		try {
			GoogleIdToken idToken = googleIdTokenVerifier.verify(idTokenString);
			if (idToken == null) {
				throw new UnauthorizedException(ErrorCode.INVALID_SOCIAL_TOKEN);
			}

			return idToken.getPayload().getEmail();
		} catch (UnauthorizedException e) {
			throw e;
		} catch (Exception e) {
			throw new UnauthorizedException(ErrorCode.INVALID_SOCIAL_TOKEN);
		}
	}
}
