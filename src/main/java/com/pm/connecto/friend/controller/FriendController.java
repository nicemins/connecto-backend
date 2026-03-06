package com.pm.connecto.friend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pm.connecto.common.context.UserContext;
import com.pm.connecto.common.response.ApiResponse;
import com.pm.connecto.friend.dto.FriendRequestCreateRequest;
import com.pm.connecto.friend.dto.FriendRequestResponse;
import com.pm.connecto.friend.dto.FriendResponse;
import com.pm.connecto.friend.service.FriendService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "친구", description = "친구 요청 및 친구 목록 관리 API")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequestMapping("/friends")
public class FriendController {

	private final FriendService friendService;
	private final UserContext userContext;

	public FriendController(FriendService friendService, UserContext userContext) {
		this.friendService = friendService;
		this.userContext = userContext;
	}

	@Operation(summary = "친구 목록 조회", description = "나의 친구 목록을 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
	@GetMapping
	public ApiResponse<List<FriendResponse>> getFriends() {
		return ApiResponse.success(friendService.getFriends(userContext.getUserId()));
	}

	@Operation(summary = "받은 친구 요청 목록 조회", description = "수락 대기 중인 친구 요청 목록을 조회합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
	@GetMapping("/requests")
	public ApiResponse<List<FriendRequestResponse>> getPendingRequests() {
		return ApiResponse.success(friendService.getPendingRequests(userContext.getUserId()));
	}

	@Operation(summary = "친구 요청 전송", description = "다른 사용자에게 친구 요청을 보냅니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "요청 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자 없음"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 요청 존재 또는 이미 친구")
	})
	@PostMapping("/request")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<FriendRequestResponse> sendFriendRequest(
		@Valid @RequestBody FriendRequestCreateRequest request
	) {
		return ApiResponse.success(
			friendService.sendFriendRequest(userContext.getUserId(), request.receiverId())
		);
	}

	@Operation(summary = "친구 요청 수락", description = "받은 친구 요청을 수락합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수락 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "요청 없음")
	})
	@PatchMapping("/request/{requestId}/accept")
	public ApiResponse<FriendRequestResponse> acceptFriendRequest(
		@Parameter(description = "친구 요청 ID") @PathVariable Long requestId
	) {
		return ApiResponse.success(
			friendService.acceptFriendRequest(userContext.getUserId(), requestId)
		);
	}

	@Operation(summary = "친구 요청 거절", description = "받은 친구 요청을 거절합니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "거절 성공"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "요청 없음")
	})
	@PatchMapping("/request/{requestId}/reject")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void rejectFriendRequest(
		@Parameter(description = "친구 요청 ID") @PathVariable Long requestId
	) {
		friendService.rejectFriendRequest(userContext.getUserId(), requestId);
	}
}
