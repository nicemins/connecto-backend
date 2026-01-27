package com.pm.connecto.match.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pm.connecto.common.context.UserContext;
import com.pm.connecto.common.response.ApiResponse;
import com.pm.connecto.match.dto.MatchResultResponse;
import com.pm.connecto.match.dto.MatchStartResponse;
import com.pm.connecto.match.dto.MatchStatusResponse;
import com.pm.connecto.match.service.MatchService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 매칭 API
 * - 대기열 진입/이탈
 * - 매칭 상태 확인
 * - 통화 종료 후 프로필 조회
 * - spring.data.redis.host가 명시적으로 설정되어 있을 때만 활성화 (테스트 환경에서는 비활성화)
 */
@Tag(name = "매칭", description = "랜덤 매칭 및 통화 세션 관리 API")
@RestController
@RequestMapping("/match")
@ConditionalOnProperty(name = "spring.data.redis.host", matchIfMissing = false)
public class MatchController {

	private final MatchService matchService;
	private final UserContext userContext;

	public MatchController(MatchService matchService, UserContext userContext) {
		this.matchService = matchService;
		this.userContext = userContext;
	}

	@Operation(summary = "매칭 시작", description = "대기열에 진입하여 매칭을 시작합니다.")
	@SecurityRequirement(name = "Bearer Authentication")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "매칭 시작 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
	})
	@PostMapping("/start")
	public ApiResponse<MatchStartResponse> startMatching() {
		MatchStartResponse response = matchService.startMatching(userContext.getUserId());
		return ApiResponse.success(response);
	}

	@Operation(summary = "매칭 취소", description = "대기열에서 이탈합니다.")
	@SecurityRequirement(name = "Bearer Authentication")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "취소 성공")
	@PostMapping("/cancel")
	public ApiResponse<Void> cancelMatching() {
		matchService.cancelMatching(userContext.getUserId());
		return ApiResponse.success(null);
	}

	@Operation(summary = "매칭 상태 확인", description = "현재 매칭 상태와 WebRTC 방 정보를 조회합니다.")
	@SecurityRequirement(name = "Bearer Authentication")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
	@GetMapping("/status")
	public ApiResponse<MatchStatusResponse> getMatchStatus() {
		MatchStatusResponse response = matchService.getMatchStatus(userContext.getUserId());
		return ApiResponse.success(response);
	}

	@Operation(summary = "통화 종료 후 상대방 프로필 조회", description = "통화가 종료된 세션의 상대방 프로필을 조회합니다. (권한 필수)")
	@SecurityRequirement(name = "Bearer Authentication")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "접근 권한 없음"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "세션 없음")
	})
	@GetMapping("/result/{sessionId}")
	public ApiResponse<MatchResultResponse> getMatchResult(
		@Parameter(description = "통화 세션 ID", example = "1")
		@PathVariable Long sessionId
	) {
		MatchResultResponse response = matchService.getMatchResult(sessionId, userContext.getUserId());
		return ApiResponse.success(response);
	}
}
