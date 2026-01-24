package com.pm.connecto.profile.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pm.connecto.common.response.ApiResponse;
import com.pm.connecto.profile.domain.Profile;
import com.pm.connecto.profile.dto.ProfileResponse;
import com.pm.connecto.profile.service.ProfileService;
import com.pm.connecto.user.dto.AvailabilityResponse;

import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 프로필 공개 API Controller
 * 
 * <p>본인 프로필 관련 API는 UserController의 /users/me/* 사용:
 * <ul>
 *   <li>GET /users/me - 내 전체 정보 조회 (프로필 포함)</li>
 *   <li>GET /users/me/profile - 내 프로필 조회</li>
 *   <li>POST /users/me/profile - 내 프로필 생성 (최초 1회)</li>
 *   <li>PATCH /users/me/profile - 내 프로필 수정</li>
 * </ul>
 * 
 * <p>이 Controller는 공개 API만 제공:
 * <ul>
 *   <li>GET /profiles/{profileId} - 타인 프로필 조회</li>
 *   <li>GET /profiles/exists?nickname= - 닉네임 중복 확인</li>
 * </ul>
 */
@RestController
@RequestMapping("/profiles")
@Validated
public class ProfileController {

	private final ProfileService profileService;

	public ProfileController(ProfileService profileService) {
		this.profileService = profileService;
	}

	/**
	 * 다른 사용자 프로필 조회 (공개 API)
	 */
	@GetMapping("/{profileId}")
	public ApiResponse<ProfileResponse> getProfile(@PathVariable Long profileId) {
		Profile profile = profileService.getProfileById(profileId);
		return ApiResponse.success(ProfileResponse.from(profile));
	}

	/**
	 * 닉네임 중복 확인 (공개 API)
	 */
	@GetMapping("/exists")
	public ApiResponse<AvailabilityResponse> checkNicknameAvailable(
		@RequestParam @NotBlank(message = "닉네임은 필수입니다.") @Size(min = 2, max = 30, message = "닉네임은 2자 이상 30자 이하여야 합니다.") String nickname
	) {
		boolean available = profileService.isNicknameAvailable(nickname);
		return ApiResponse.success(new AvailabilityResponse(available));
	}
}
