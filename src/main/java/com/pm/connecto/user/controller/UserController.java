package com.pm.connecto.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pm.connecto.common.response.ApiResponse;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.dto.AvailabilityResponse;
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

	@GetMapping("/me")
	public ApiResponse<UserResponse> getMe(HttpServletRequest request) {
		Long userId = (Long) request.getAttribute("userId");
		User user = userService.getMe(userId);
		return ApiResponse.success(UserResponse.from(user));
	}

	@PutMapping("/me")
	public ApiResponse<UserResponse> updateMe(
		HttpServletRequest request,
		@RequestBody UserUpdateRequest updateRequest
	) {
		Long userId = (Long) request.getAttribute("userId");
		User user = userService.updateUser(userId, updateRequest.nickname(), updateRequest.password());
		return ApiResponse.success(UserResponse.from(user));
	}

	@DeleteMapping("/me")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteMe(HttpServletRequest request) {
		Long userId = (Long) request.getAttribute("userId");
		userService.deleteUser(userId);
	}

	@GetMapping("/exists")
	public ApiResponse<AvailabilityResponse> checkExists(
		@RequestParam(required = false) String email,
		@RequestParam(required = false) String nickname
	) {
		boolean available = true;

		if (email != null && !email.isBlank()) {
			available = userService.isEmailAvailable(email);
		} else if (nickname != null && !nickname.isBlank()) {
			available = userService.isNicknameAvailable(nickname);
		}

		return ApiResponse.success(new AvailabilityResponse(available));
	}
}
