package com.pm.connecto.match.dto;

import com.pm.connecto.profile.dto.ProfileResponse;

/**
 * 통화 종료 후 상대방 프로필 조회 응답
 */
public record MatchResultResponse(
	ProfileResponse profile,
	boolean wantAgain
) {
}
