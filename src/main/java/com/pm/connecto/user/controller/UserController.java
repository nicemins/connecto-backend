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

import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 사용자 API Controller
 * 
 * <p>모든 본인 관련 API는 /users/me 하위에 통합:
 * <ul>
 *   <li>/users/me - 사용자 기본 정보</li>
 *   <li>/users/me/profile - 프로필 정보</li>
 *   <li>/users/me/languages - 언어 정보 (LanguageController)</li>
 *   <li>/users/me/interests - 관심사 정보 (InterestController)</li>
 * </ul>
 */
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

	/**
	 * 로그인 사용자 통합 정보 조회
	 * - User + Profile + Languages + Interests 반환
	 */
	@GetMapping("/me")
	public ApiResponse<UserMeResponse> getMe() {
		return ApiResponse.success(userMeService.getMyInfo(userContext.getUserId()));
	}

	/**
	 * 사용자 정보 수정 (비밀번호)
	 */
	@PutMapping("/me")
	public ApiResponse<UserResponse> updateMe(@Valid @RequestBody UserUpdateRequest updateRequest) {
		User user = userService.updateUser(
			userContext.getUserId(),
			updateRequest.password()
		);
		return ApiResponse.success(UserResponse.from(user));
	}

	/**
	 * 회원 탈퇴 (Soft Delete)
	 */
	@DeleteMapping("/me")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteMe() {
		userService.deleteUser(userContext.getUserId());
	}

	// ========== /users/me/profile - 프로필 ==========

	/**
	 * 내 프로필 조회
	 */
	@GetMapping("/me/profile")
	public ApiResponse<ProfileResponse> getMyProfile() {
		Profile profile = profileService.getProfile(userContext.getUserId());
		return ApiResponse.success(ProfileResponse.from(profile));
	}

	/**
	 * 내 프로필 생성 (최초 1회)
	 * - 이미 프로필이 있으면 409 Conflict
	 * - nickname 필수
	 */
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

	/**
	 * 내 프로필 수정
	 * - 프로필 없으면 404 Not Found
	 * - nickname은 선택 (null이면 변경 안 함)
	 */
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

	// ========== /users/exists - 중복 확인 ==========

	/**
	 * 이메일 중복 확인
	 * - 닉네임 중복 확인은 GET /profiles/exists?nickname= 사용
	 */
	@GetMapping("/exists/email")
	public ApiResponse<AvailabilityResponse> checkEmailExists(
		@RequestParam @NotBlank(message = "이메일은 필수입니다.") @Email(message = "올바른 이메일 형식이 아닙니다.") String email
	) {
		boolean available = userService.isEmailAvailable(email);
		return ApiResponse.success(new AvailabilityResponse(available));
	}
}
