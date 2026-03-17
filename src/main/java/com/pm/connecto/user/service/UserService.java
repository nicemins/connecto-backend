package com.pm.connecto.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pm.connecto.common.exception.DuplicateResourceException;
import com.pm.connecto.common.exception.ResourceNotFoundException;
import com.pm.connecto.common.response.ErrorCode;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.repository.UserRepository;

@Service
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
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
