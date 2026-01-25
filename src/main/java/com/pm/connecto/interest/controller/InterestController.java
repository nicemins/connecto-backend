package com.pm.connecto.interest.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pm.connecto.common.context.UserContext;
import com.pm.connecto.common.response.ApiResponse;
import com.pm.connecto.interest.domain.Interest;
import com.pm.connecto.interest.dto.InterestResponse;
import com.pm.connecto.interest.dto.UserInterestRequest;
import com.pm.connecto.interest.service.InterestService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "관심사", description = "관심사 조회 및 사용자 관심사 관리 API")
@RestController
@RequestMapping
public class InterestController {

	private final InterestService interestService;
	private final UserContext userContext;

	public InterestController(InterestService interestService, UserContext userContext) {
		this.interestService = interestService;
		this.userContext = userContext;
	}

	// ========== 전체 관심사 목록 (마스터 데이터, 공개 API) ==========

	@Operation(summary = "전체 관심사 목록", description = "등록된 모든 관심사 태그를 조회합니다.")
	@SecurityRequirements  // 인증 불필요 명시
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
	@GetMapping("/interests")
	public ApiResponse<List<InterestResponse>> getAllInterests() {
		List<Interest> interests = interestService.getAllInterests();
		return ApiResponse.success(
			interests.stream()
				.map(InterestResponse::from)
				.toList()
		);
	}

	// ========== 사용자 관심사 ==========

	@Operation(summary = "내 관심사 추가", description = "관심사를 추가합니다. 존재하지 않는 태그는 자동 생성됩니다.")
	@SecurityRequirement(name = "Bearer Authentication")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "추가 성공")
	@PostMapping("/users/me/interests")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<List<InterestResponse>> addUserInterests(@Valid @RequestBody UserInterestRequest userInterestRequest) {
		List<Interest> interests = interestService.addUserInterests(
			userContext.getUserId(),
			userInterestRequest.interestNames()
		);

		return ApiResponse.success(
			interests.stream()
				.map(InterestResponse::from)
				.toList()
		);
	}

	@Operation(summary = "내 관심사 조회", description = "등록된 관심사 목록을 조회합니다.")
	@SecurityRequirement(name = "Bearer Authentication")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
	@GetMapping("/users/me/interests")
	public ApiResponse<List<InterestResponse>> getUserInterests() {
		List<Interest> interests = interestService.getUserInterests(userContext.getUserId());
		return ApiResponse.success(
			interests.stream()
				.map(InterestResponse::from)
				.toList()
		);
	}

	@Operation(summary = "내 관심사 전체 교체", description = "기존 관심사를 모두 삭제하고 새로운 목록으로 교체합니다.")
	@SecurityRequirement(name = "Bearer Authentication")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "교체 성공")
	@PutMapping("/users/me/interests")
	public ApiResponse<List<InterestResponse>> replaceUserInterests(@Valid @RequestBody UserInterestRequest userInterestRequest) {
		List<Interest> interests = interestService.replaceUserInterests(
			userContext.getUserId(),
			userInterestRequest.interestNames()
		);

		return ApiResponse.success(
			interests.stream()
				.map(InterestResponse::from)
				.toList()
		);
	}

	@Operation(summary = "내 관심사 삭제", description = "특정 관심사를 삭제합니다.")
	@SecurityRequirement(name = "Bearer Authentication")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "관심사 없음")
	})
	@DeleteMapping("/users/me/interests/{interestId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeUserInterest(
		@Parameter(description = "관심사 ID", example = "1")
		@PathVariable Long interestId
	) {
		interestService.removeUserInterestById(userContext.getUserId(), interestId);
	}
}
