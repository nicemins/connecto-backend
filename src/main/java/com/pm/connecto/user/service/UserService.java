package com.pm.connecto.user.service;

import org.springframework.stereotype.Service;

import com.pm.connecto.user.domain.User;
import com.pm.connecto.auth.jwt.JwtTokenProvider;
import com.pm.connecto.user.repository.UserRepository;

@Service
public class UserService {

	private final UserRepository userRepository;
	private final JwtTokenProvider jwtTokenProvider;

	public UserService(UserRepository userRepository, JwtTokenProvider jwtTokenProvider) {
		this.userRepository = userRepository;
		this.jwtTokenProvider = jwtTokenProvider;
	}

	public User createUser(String email, String nickname, String password) {
		if (userRepository.findByEmail(email).isPresent()) {
			throw new IllegalArgumentException("이미 존재하는 이메일입니다: " + email);
		}

		User user = new User(email, nickname, password);
		return userRepository.save(user);
	}

	public String login(String email, String password) {
		User user = userRepository.findByEmail(email)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다: " + email));

		if (!user.getPassword().equals(password)) {
			throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
		}

		return jwtTokenProvider.generateToken(user.getId());
	}

	public User getMe(Long userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
	}
}
