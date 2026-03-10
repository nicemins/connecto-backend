package com.pm.connecto.notification.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pm.connecto.common.context.UserContext;
import com.pm.connecto.common.response.ApiResponse;
import com.pm.connecto.notification.dto.DeviceTokenRequest;
import com.pm.connecto.notification.service.FcmService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "디바이스 토큰", description = "FCM 푸시 알림 토큰 관리 API")
@RestController
@RequestMapping("/users/me")
public class DeviceTokenController {

	private final FcmService fcmService;
	private final UserContext userContext;

	public DeviceTokenController(FcmService fcmService, UserContext userContext) {
		this.fcmService = fcmService;
		this.userContext = userContext;
	}

	@Operation(summary = "FCM 토큰 등록/갱신", description = "디바이스 FCM 토큰을 서버에 등록합니다.")
	@PostMapping("/device-token")
	public ApiResponse<Void> registerToken(@Valid @RequestBody DeviceTokenRequest request) {
		fcmService.registerToken(userContext.getUserId(), request.token(), request.platform());
		return ApiResponse.success(null);
	}

	@Operation(summary = "FCM 토큰 삭제", description = "디바이스 FCM 토큰을 삭제합니다.")
	@DeleteMapping("/device-token")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteToken(@Valid @RequestBody DeviceTokenRequest request) {
		fcmService.deleteToken(userContext.getUserId(), request.token());
	}
}
