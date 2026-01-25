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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 프로필 API (공개)
 * - 인증 없이 접근 가능
 * - 공개 프로필 조회 및 닉네임 중복 확인
 */
@Tag(name = "프로필", description = "공개 프로필 조회 및 닉네임 중복 확인 API")
@SecurityRequirements  // 인증 불필요 명시 (Swagger UI에서 자물쇠 아이콘 제거)
@RestController
@RequestMapping("/profiles")
@Validated
public class ProfileController {

	private final ProfileService profileService;

	public ProfileController(ProfileService profileService) {
		this.profileService = profileService;
	}

	@Operation(summary = "프로필 조회", description = "프로필 ID로 사용자 프로필을 조회합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "프로필 없음")
	})
	@GetMapping("/{profileId}")
	public ApiResponse<ProfileResponse> getProfile(
		@Parameter(description = "프로필 ID", example = "1")
		@PathVariable Long profileId
	) {
		Profile profile = profileService.getProfileById(profileId);
		return ApiResponse.success(ProfileResponse.from(profile));
	}

	@Operation(summary = "닉네임 중복 확인", description = "닉네임 사용 가능 여부를 확인합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "확인 완료")
	@GetMapping("/exists")
	public ApiResponse<AvailabilityResponse> checkNicknameAvailable(
		@Parameter(description = "확인할 닉네임", example = "홍길동")
		@RequestParam @NotBlank(message = "닉네임은 필수입니다.") @Size(min = 2, max = 50, message = "닉네임은 2자 이상 50자 이하여야 합니다.") String nickname
	) {
		boolean available = profileService.isNicknameAvailable(nickname);
		return ApiResponse.success(new AvailabilityResponse(available));
	}
}
