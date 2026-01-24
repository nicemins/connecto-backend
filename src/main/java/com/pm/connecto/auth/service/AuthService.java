package com.pm.connecto.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.pm.connecto.auth.jwt.JwtTokenProvider;
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
		User user = userRepository.findByEmail(email)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다: " + email));

		if (user.isDeleted()) {
			throw new IllegalArgumentException("탈퇴한 사용자입니다.");
		}

		if (!passwordEncoder.matches(password, user.getPassword())) {
			throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
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
			throw new IllegalArgumentException("유효하지 않은 Refresh Token입니다.");
		}

		Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
		return jwtTokenProvider.generateAccessToken(userId);
	}

	public long getRefreshExpiration() {
		return jwtTokenProvider.getRefreshExpiration();
	}
}
