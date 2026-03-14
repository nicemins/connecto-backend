package com.pm.connecto.webrtc.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pm.connecto.common.context.UserContext;
import com.pm.connecto.common.response.ApiResponse;
import com.pm.connecto.webrtc.dto.TurnCredentialResponse;
import com.pm.connecto.webrtc.service.TurnCredentialService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "WebRTC", description = "WebRTC 연결 설정 API")
@RestController
@RequestMapping("/webrtc")
public class TurnCredentialController {

	private final TurnCredentialService turnCredentialService;
	private final UserContext userContext;

	public TurnCredentialController(TurnCredentialService turnCredentialService, UserContext userContext) {
		this.turnCredentialService = turnCredentialService;
		this.userContext = userContext;
	}

	@Operation(summary = "TURN 자격증명 조회", description = "단기 TURN 자격증명을 발급합니다. TTL 1시간.")
	@SecurityRequirement(name = "bearerAuth")
	@GetMapping("/turn-credentials")
	public ApiResponse<TurnCredentialResponse> getTurnCredentials() {
		Long userId = userContext.getUserId();
		return ApiResponse.success(turnCredentialService.generateCredentials(userId));
	}
}
