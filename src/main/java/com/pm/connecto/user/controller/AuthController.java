package com.pm.connecto.user.controller;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pm.connecto.auth.service.AuthService;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.dto.LoginRequest;
import com.pm.connecto.user.dto.LoginResponse;

@RestController
@RequestMapping("/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
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
			.body(new LoginResponse(accessToken));
	}

	@PostMapping("/refresh")
	public LoginResponse refresh(@CookieValue("refreshToken") String refreshToken) {
		String accessToken = authService.refreshAccessToken(refreshToken);
		return new LoginResponse(accessToken);
	}

	@PostMapping("/logout")
	public ResponseEntity<Map<String, String>> logout() {
		ResponseCookie deleteCookie = ResponseCookie.from("refreshToken", "")
			.httpOnly(true)
			.secure(true)
			.path("/")
			.maxAge(0)
			.sameSite("Strict")
			.build();

		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
			.body(Map.of("message", "logout success"));
	}
}
