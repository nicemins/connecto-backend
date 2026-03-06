package com.pm.connecto.interest.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pm.connecto.common.context.UserContext;
import com.pm.connecto.common.response.ApiResponse;
import com.pm.connecto.interest.domain.Interest;
import com.pm.connecto.interest.dto.InterestCreateRequest;
import com.pm.connecto.interest.dto.InterestResponse;
import com.pm.connecto.interest.service.InterestService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "관심사", description = "사용자 관심사 설정 API")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequestMapping("/users/me/interests")
public class InterestController {

	private final InterestService interestService;
	private final UserContext userContext;

	public InterestController(InterestService interestService, UserContext userContext) {
		this.interestService = interestService;
		this.userContext = userContext;
	}

	@Operation(summary = "관심사 추가", description = "새로운 관심사 태그를 추가합니다. (최대 10개)")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "추가 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 등록된 관심사")
	})
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<InterestResponse> addInterest(@Valid @RequestBody InterestCreateRequest request) {
		Interest interest = interestService.addInterest(userContext.getUserId(), request.tag());
		return ApiResponse.success(InterestResponse.from(interest));
	}

	@Operation(summary = "관심사 목록 조회", description = "등록된 관심사 목록을 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
	@GetMapping
	public ApiResponse<List<InterestResponse>> getInterests() {
		return ApiResponse.success(
			interestService.getInterests(userContext.getUserId()).stream()
				.map(InterestResponse::from)
				.toList()
		);
	}

	@Operation(summary = "관심사 삭제", description = "특정 관심사를 삭제합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "관심사 없음")
	})
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteInterest(
		@Parameter(description = "관심사 ID", example = "1")
		@PathVariable Long id
	) {
		interestService.deleteInterest(userContext.getUserId(), id);
	}
}
