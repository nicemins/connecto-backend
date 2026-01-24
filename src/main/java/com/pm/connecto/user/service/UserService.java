package com.pm.connecto.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pm.connecto.user.domain.User;
import com.pm.connecto.auth.jwt.JwtTokenProvider;
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

	public User createUser(String email, String nickname, String password) {
		if (userRepository.findByEmail(email).isPresent()) {
			throw new IllegalArgumentException("이미 존재하는 이메일입니다: " + email);
		}

		String encodedPassword = passwordEncoder.encode(password);
		User user = new User(email, nickname, encodedPassword);
		return userRepository.save(user);
	}

	public String login(String email, String password) {
		User user = userRepository.findByEmail(email)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다: " + email));

		if (user.isDeleted()) {
			throw new IllegalArgumentException("탈퇴한 사용자입니다.");
		}

		if (!passwordEncoder.matches(password, user.getPassword())) {
			throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
		}

		return jwtTokenProvider.generateAccessToken(user.getId());
	}

	public User getMe(Long userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
	}

	public boolean isEmailAvailable(String email) {
		return !userRepository.existsByEmail(email);
	}

	public boolean isNicknameAvailable(String nickname) {
		return !userRepository.existsByNickname(nickname);
	}

	@Transactional
	public User updateUser(Long userId, String nickname, String password) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

		user.updateNickname(nickname);

		if (password != null && !password.isBlank()) {
			String encodedPassword = passwordEncoder.encode(password);
			user.updatePassword(encodedPassword);
		}

		return user;
	}

	@Transactional
	public void deleteUser(Long userId) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

		user.delete();
	}
}
