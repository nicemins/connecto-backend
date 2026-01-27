package com.pm.connecto.call.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pm.connecto.common.context.UserContext;
import com.pm.connecto.common.response.ApiResponse;
import com.pm.connecto.call.dto.CallAgainRequest;
import com.pm.connecto.call.dto.CallEndRequest;
import com.pm.connecto.call.service.CallService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 통화 API
 * - 통화 종료 처리
 * - 재연결 의사 표현
 */
@Tag(name = "통화", description = "통화 종료 및 재연결 관리 API")
@RestController
@RequestMapping("/call")
public class CallController {

	private final CallService callService;
	private final UserContext userContext;

	public CallController(CallService callService, UserContext userContext) {
		this.callService = callService;
		this.userContext = userContext;
	}

	@Operation(summary = "통화 종료", description = "통화를 종료하고 세션을 종료 상태로 변경합니다.")
	@SecurityRequirement(name = "Bearer Authentication")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "종료 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "접근 권한 없음")
	})
	@PostMapping("/end")
	public ApiResponse<Void> endCall(@Valid @RequestBody CallEndRequest request) {
		callService.endCall(request.sessionId(), userContext.getUserId());
		return ApiResponse.success(null);
	}

	@Operation(summary = "재연결 의사 표현", description = "통화 종료 후 상대방과 다시 통화하고 싶은 의사를 표현합니다. (👍)")
	@SecurityRequirement(name = "Bearer Authentication")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "의사 표현 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "접근 권한 없음")
	})
	@PostMapping("/again")
	public ApiResponse<Void> expressCallAgain(@Valid @RequestBody CallAgainRequest request) {
		callService.expressCallAgain(request.sessionId(), userContext.getUserId(), request.wantAgain());
		return ApiResponse.success(null);
	}
}
