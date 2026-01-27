package com.pm.connecto.match.dto;

/**
 * 매칭 상태 응답
 */
public record MatchStatusResponse(
	String status,
	Long sessionId,
	String webrtcChannelId
) {
	public static MatchStatusResponse waiting() {
		return new MatchStatusResponse("WAITING", null, null);
	}

	public static MatchStatusResponse matched(Long sessionId, String webrtcChannelId) {
		return new MatchStatusResponse("MATCHED", sessionId, webrtcChannelId);
	}
}
