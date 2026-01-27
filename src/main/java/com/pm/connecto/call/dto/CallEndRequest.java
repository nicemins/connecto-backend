package com.pm.connecto.call.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 통화 종료 요청
 */
public record CallEndRequest(
	@NotNull(message = "세션 ID는 필수입니다.")
	Long sessionId
) {
}
