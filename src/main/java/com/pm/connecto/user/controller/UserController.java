package com.pm.connecto.user.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.dto.AvailabilityResponse;
import com.pm.connecto.user.dto.UserCreateRequest;
import com.pm.connecto.user.dto.UserResponse;
import com.pm.connecto.user.dto.UserUpdateRequest;
import com.pm.connecto.user.service.UserService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public User createUser(@RequestBody UserCreateRequest request) {
		return userService.createUser(request.email(), request.nickname(), request.password());
	}

	@GetMapping("/me")
	public UserResponse getMe(HttpServletRequest request) {
		Long userId = (Long) request.getAttribute("userId");
		User user = userService.getMe(userId);
		return new UserResponse(user.getId(), user.getEmail(), user.getNickname());
	}

	@GetMapping("/check-email")
	public AvailabilityResponse checkEmail(@RequestParam String email) {
		boolean available = userService.isEmailAvailable(email);
		return new AvailabilityResponse(available);
	}

	@GetMapping("/check-nickname")
	public AvailabilityResponse checkNickname(@RequestParam String nickname) {
		boolean available = userService.isNicknameAvailable(nickname);
		return new AvailabilityResponse(available);
	}

	@PutMapping("/me")
	public UserResponse updateMe(HttpServletRequest request, @RequestBody UserUpdateRequest updateRequest) {
		Long userId = (Long) request.getAttribute("userId");
		User user = userService.updateUser(userId, updateRequest.nickname(), updateRequest.password());
		return new UserResponse(user.getId(), user.getEmail(), user.getNickname());
	}

	@DeleteMapping("/me")
	public Map<String, String> deleteMe(HttpServletRequest request) {
		Long userId = (Long) request.getAttribute("userId");
		userService.deleteUser(userId);
		return Map.of("message", "회원 탈퇴가 완료되었습니다.");
	}
}
