package com.pm.connecto.user.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pm.connecto.auth.service.AuthService;
import com.pm.connecto.common.response.ApiResponse;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.dto.LoginRequest;
import com.pm.connecto.user.dto.LoginResponse;
import com.pm.connecto.user.dto.UserCreateRequest;
import com.pm.connecto.user.dto.UserResponse;
import com.pm.connecto.user.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

	private final AuthService authService;
	private final UserService userService;

	public AuthController(AuthService authService, UserService userService) {
		this.authService = authService;
		this.userService = userService;
	}

	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<UserResponse> signup(@Valid @RequestBody UserCreateRequest request) {
		User user = userService.createUser(request.email(), request.password());
		return ApiResponse.success(UserResponse.from(user));
	}

	@PostMapping("/login")
	public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
		User user = authService.authenticate(request.email(), request.password());

		String accessToken = authService.generateAccessToken(user.getId());
		String refreshToken = authService.generateRefreshToken(user.getId());

		ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
			.httpOnly(true)
			.secure(true)
			.path("/")
			.maxAge(authService.getRefreshExpiration() / 1000)
			.sameSite("Strict")
			.build();

		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
			.body(ApiResponse.success(new LoginResponse(accessToken)));
	}

	@PostMapping("/refresh")
	public ApiResponse<LoginResponse> refresh(@CookieValue("refreshToken") String refreshToken) {
		String accessToken = authService.refreshAccessToken(refreshToken);
		return ApiResponse.success(new LoginResponse(accessToken));
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public ResponseEntity<Void> logout() {
		ResponseCookie deleteCookie = ResponseCookie.from("refreshToken", "")
			.httpOnly(true)
			.secure(true)
			.path("/")
			.maxAge(0)
			.sameSite("Strict")
			.build();

		return ResponseEntity.noContent()
			.header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
			.build();
	}
}
