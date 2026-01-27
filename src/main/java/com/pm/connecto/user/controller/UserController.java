package com.pm.connecto.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pm.connecto.common.context.UserContext;
import com.pm.connecto.common.response.ApiResponse;
import com.pm.connecto.profile.domain.Profile;
import com.pm.connecto.profile.dto.ProfileCreateRequest;
import com.pm.connecto.profile.dto.ProfileResponse;
import com.pm.connecto.profile.dto.ProfileUpdateRequest;
import com.pm.connecto.profile.service.ProfileService;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.dto.AvailabilityResponse;
import com.pm.connecto.user.dto.UserMeResponse;
import com.pm.connecto.user.dto.UserResponse;
import com.pm.connecto.user.dto.UserUpdateRequest;
import com.pm.connecto.user.service.UserMeService;
import com.pm.connecto.user.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Tag(name = "마이페이지", description = "로그인 사용자의 정보 및 프로필 관리 API")
@RestController
@RequestMapping("/users")
@Validated
public class UserController {

	private final UserService userService;
	private final UserMeService userMeService;
	private final ProfileService profileService;
	private final UserContext userContext;

	public UserController(
		UserService userService,
		UserMeService userMeService,
		ProfileService profileService,
		UserContext userContext
	) {
		this.userService = userService;
		this.userMeService = userMeService;
		this.profileService = profileService;
		this.userContext = userContext;
	}

	// ========== /users/me - 사용자 기본 ==========

	@Operation(summary = "내 정보 조회", description = "로그인한 사용자의 통합 정보(프로필, 언어, 관심사 포함)를 조회합니다.")
	@SecurityRequirement(name = "Bearer Authentication")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
	})
	@GetMapping("/me")
	public ApiResponse<UserMeResponse> getMe() {
		return ApiResponse.success(userMeService.getMyInfo(userContext.getUserId()));
	}

	@Operation(summary = "내 정보 수정", description = "비밀번호를 수정합니다.")
	@SecurityRequirement(name = "Bearer Authentication")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
	})
	@PutMapping("/me")
	public ApiResponse<UserResponse> updateMe(@Valid @RequestBody UserUpdateRequest updateRequest) {
		User user = userService.updateUser(
			userContext.getUserId(),
			updateRequest.password()
		);
		return ApiResponse.success(UserResponse.from(user));
	}

	@Operation(summary = "회원 탈퇴", description = "계정을 삭제합니다. (Soft Delete)")
	@SecurityRequirement(name = "Bearer Authentication")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "탈퇴 성공")
	@DeleteMapping("/me")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteMe() {
		userService.deleteUser(userContext.getUserId());
	}

	// ========== /users/me/profile - 프로필 ==========

	@Operation(summary = "내 프로필 조회", description = "로그인한 사용자의 프로필을 조회합니다.")
	@SecurityRequirement(name = "Bearer Authentication")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "프로필 없음")
	})
	@GetMapping("/me/profile")
	public ApiResponse<ProfileResponse> getMyProfile() {
		Profile profile = profileService.getProfile(userContext.getUserId());
		return ApiResponse.success(ProfileResponse.from(profile));
	}

	@Operation(summary = "내 프로필 생성", description = "프로필을 생성합니다. (최초 1회만 가능)")
	@SecurityRequirement(name = "Bearer Authentication")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "프로필 이미 존재 또는 닉네임 중복")
	})
	@PostMapping("/me/profile")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<ProfileResponse> createMyProfile(@Valid @RequestBody ProfileCreateRequest createRequest) {
		Profile profile = profileService.createProfile(
			userContext.getUserId(),
			createRequest.nickname(),
			createRequest.profileImageUrl(),
			createRequest.bio()
		);
		return ApiResponse.success(ProfileResponse.from(profile));
	}

	@Operation(summary = "내 프로필 수정", description = "프로필 정보를 수정합니다. (부분 수정 가능)")
	@SecurityRequirement(name = "Bearer Authentication")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "프로필 없음"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "닉네임 중복")
	})
	@PatchMapping("/me/profile")
	public ApiResponse<ProfileResponse> updateMyProfile(@Valid @RequestBody ProfileUpdateRequest updateRequest) {
		Profile profile = profileService.updateProfile(
			userContext.getUserId(),
			updateRequest.nickname(),
			updateRequest.profileImageUrl(),
			updateRequest.bio()
		);
		return ApiResponse.success(ProfileResponse.from(profile));
	}

	// ========== /users/exists - 중복 확인 (공개 API) ==========

	@Operation(summary = "이메일 중복 확인", description = "이메일 사용 가능 여부를 확인합니다.")
	@SecurityRequirements  // 인증 불필요 명시
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "확인 완료")
	@GetMapping("/exists/email")
	public ApiResponse<AvailabilityResponse> checkEmailExists(
		@Parameter(description = "확인할 이메일", example = "user@example.com")
		@RequestParam @NotBlank(message = "이메일은 필수입니다.") @Email(message = "올바른 이메일 형식이 아닙니다.") String email
	) {
		boolean available = userService.isEmailAvailable(email);
		return ApiResponse.success(new AvailabilityResponse(available));
	}
}
