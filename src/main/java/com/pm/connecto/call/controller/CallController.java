package com.pm.connecto.call.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pm.connecto.call.dto.CallAgainRequest;
import com.pm.connecto.call.dto.CallEndRequest;
import com.pm.connecto.call.dto.FriendCallResponse;
import com.pm.connecto.call.service.CallService;
import com.pm.connecto.common.context.UserContext;
import com.pm.connecto.common.response.ApiResponse;

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
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "세션 없음 또는 권한 없음"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 종료된 세션")
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
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "세션 없음 또는 권한 없음"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "종료되지 않은 세션")
	})
	@PostMapping("/again")
	public ApiResponse<Void> expressCallAgain(@Valid @RequestBody CallAgainRequest request) {
		callService.expressCallAgain(request.sessionId(), userContext.getUserId(), request.wantAgain());
		return ApiResponse.success(null);
	}

	@Operation(summary = "통화 거절", description = "수신된 통화를 거절합니다. 발신자에게 즉시 call:rejected 소켓 이벤트가 전송됩니다.")
	@SecurityRequirement(name = "Bearer Authentication")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "거절 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "세션 없음 또는 권한 없음"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 종료된 세션")
	})
	@PostMapping("/reject/{sessionId}")
	public ApiResponse<Void> rejectCall(
		@io.swagger.v3.oas.annotations.Parameter(description = "세션 ID") @PathVariable Long sessionId
	) {
		callService.rejectCall(sessionId, userContext.getUserId());
		return ApiResponse.success(null);
	}

	@Operation(summary = "친구에게 통화 요청", description = "친구에게 1:1 통화를 요청합니다. 친구 관계여야만 요청 가능합니다.")
	@SecurityRequirement(name = "Bearer Authentication")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "요청 성공 — sessionId, webrtcChannelId 반환"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "친구 관계가 아닙니다."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 통화 중")
	})
	@PostMapping("/request/{friendId}")
	@org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<FriendCallResponse> requestCallToFriend(
		@io.swagger.v3.oas.annotations.Parameter(description = "친구 사용자 ID") @PathVariable Long friendId
	) {
		return ApiResponse.success(callService.requestCallToFriend(userContext.getUserId(), friendId));
	}
}
