package com.pm.connecto.call.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 재연결 의사 표현 요청
 */
public record CallAgainRequest(
	@NotNull(message = "세션 ID는 필수입니다.")
	Long sessionId,
	@NotNull(message = "재연결 의사는 필수입니다.")
	Boolean wantAgain
) {
}
