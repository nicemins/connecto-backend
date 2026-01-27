package com.pm.connecto.match.dto;

/**
 * 매칭 시작 응답
 */
public record MatchStartResponse(
	boolean matched,
	Long sessionId,
	String webrtcChannelId
) {
	public static MatchStartResponse waiting() {
		return new MatchStartResponse(false, null, null);
	}

	public static MatchStartResponse matched(Long sessionId, String webrtcChannelId) {
		return new MatchStartResponse(true, sessionId, webrtcChannelId);
	}
}
