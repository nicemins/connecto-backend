package com.pm.connecto.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.dto.UserCreateRequest;
import com.pm.connecto.user.dto.UserResponse;
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
}
