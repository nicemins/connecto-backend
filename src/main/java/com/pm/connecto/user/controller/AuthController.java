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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 인증 API
 * - 인증 없이 접근 가능한 회원가입, 로그인, 토큰 관리 API
 */
@Tag(name = "인증", description = "회원가입, 로그인, 토큰 관리 API")
@SecurityRequirements  // 인증 불필요 명시 (Swagger UI에서 자물쇠 아이콘 제거)
@RestController
@RequestMapping("/auth")
public class AuthController {

	private final AuthService authService;
	private final UserService userService;

	public AuthController(AuthService authService, UserService userService) {
		this.authService = authService;
		this.userService = userService;
	}

	@Operation(summary = "회원가입", description = "이메일과 비밀번호로 새 계정을 생성합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "회원가입 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 입력값"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 존재하는 이메일")
	})
	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<UserResponse> signup(@Valid @RequestBody UserCreateRequest request) {
		User user = userService.createUser(request.email(), request.password());
		return ApiResponse.success(UserResponse.from(user));
	}

	@Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인하여 JWT 토큰을 발급받습니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
	})
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

	@Operation(summary = "토큰 갱신", description = "Refresh Token으로 새로운 Access Token을 발급받습니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토큰 갱신 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "유효하지 않은 Refresh Token")
	})
	@PostMapping("/refresh")
	public ApiResponse<LoginResponse> refresh(@CookieValue("refreshToken") String refreshToken) {
		String accessToken = authService.refreshAccessToken(refreshToken);
		return ApiResponse.success(new LoginResponse(accessToken));
	}

	@Operation(summary = "로그아웃", description = "Refresh Token 쿠키를 삭제하여 로그아웃합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "로그아웃 성공")
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
