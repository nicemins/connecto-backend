package com.pm.connecto.common.socket;

import com.corundumstudio.socketio.SocketIOClient;
import com.pm.connecto.auth.jwt.JwtTokenProvider;

/**
 * Socket.IO 클라이언트 JWT 인증 공통 유틸
 * - Authorization 헤더 또는 ?token= URL 파라미터에서 토큰 추출
 * - ChatSocketHandler, MatchSocketHandler 중복 제거용
 */
public final class SocketAuthUtil {

	private SocketAuthUtil() {}

	public static Long extractUserId(SocketIOClient client, JwtTokenProvider jwtTokenProvider) {
		try {
			String authHeader = client.getHandshakeData().getHttpHeaders().get("Authorization");
			String tokenParam = client.getHandshakeData().getSingleUrlParam("token");
			String token = null;
			if (authHeader != null && authHeader.startsWith("Bearer ")) {
				token = authHeader.substring(7);
			} else if (tokenParam != null && !tokenParam.isEmpty()) {
				token = tokenParam;
			}
			if (token == null || !jwtTokenProvider.validateToken(token)) return null;
			return jwtTokenProvider.getUserIdFromToken(token);
		} catch (Exception e) {
			return null;
		}
	}
}
